/*
 * Copyright © 2017-2020 factcast.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.factcast.store.internal;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.eventbus.EventBus;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.FactStreamPosition;
import org.factcast.core.subscription.FactStreamInfo;
import org.factcast.core.subscription.SubscriptionRequest;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.core.subscription.observer.*;
import org.factcast.store.internal.catchup.PgCatchupFactory;
import org.factcast.store.internal.listen.PgConnectionSupplier;
import org.factcast.store.internal.pipeline.ServerPipeline;
import org.factcast.store.internal.pipeline.Signal;
import org.factcast.store.internal.query.CurrentStatementHolder;
import org.factcast.store.internal.query.PgFactIdToSerialMapper;
import org.factcast.store.internal.query.PgLatestSerialFetcher;
import org.factcast.store.internal.query.PgQueryBuilder;
import org.factcast.store.internal.telemetry.PgStoreTelemetry;
import org.springframework.jdbc.core.PreparedStatementSetter;

/**
 * Creates and maintains a subscription.
 *
 * @author <uwe.schaefer@prisma-capacity.eu>
 */
@Slf4j
@RequiredArgsConstructor
public class PgFactStream {

  final PgConnectionSupplier connectionSupplier;
  final EventBus eventBus;
  final PgFactIdToSerialMapper idToSerMapper;
  final PgLatestSerialFetcher fetcher;
  final PgCatchupFactory pgCatchupFactory;
  final FastForwardTarget ffwdTarget;
  final ServerPipeline pipeline;
  final PgStoreTelemetry telemetry;

  @Getter(AccessLevel.PROTECTED)
  final SubscriptionRequestTO request;

  CondensedQueryExecutor condensedExecutor;

  @VisibleForTesting
  @Getter(AccessLevel.PROTECTED)
  final AtomicLong serial = new AtomicLong(0);

  final AtomicBoolean disconnected = new AtomicBoolean(false);

  final CurrentStatementHolder statementHolder = new CurrentStatementHolder();

  void connect() {
    log.debug("{} connect subscription {}", request, request.dump());
    // signal connect
    telemetry.onConnect(request);
    PgQueryBuilder q = new PgQueryBuilder(request.specs(), statementHolder);
    initializeSerialToStartAfter();

    if (request.streamInfo()) {
      FactStreamInfo factStreamInfo = new FactStreamInfo(serial.get(), fetcher.retrieveLatestSer());
      pipeline.process(Signal.of(factStreamInfo));
    }

    String sql = q.createSQL(null);
    PreparedStatementSetter setter = q.createStatementSetter(serial, null);
    PgSynchronizedQuery query =
        new PgSynchronizedQuery(
            request.debugInfo(),
            pipeline,
            connectionSupplier,
            sql,
            setter,
            this::isConnected,
            serial,
            fetcher,
            statementHolder);
    catchupAndFollow(request, query);
  }

  @VisibleForTesting
  void initializeSerialToStartAfter() {
    Optional<UUID> idRequestedToStartAfter = request.startingAfter();
    Long startingSerial = idRequestedToStartAfter.map(idToSerMapper::retrieve).orElse(0L);
    serial.set(startingSerial);
    log.trace("{} setting starting point to SER={}", request, startingSerial);
  }

  @VisibleForTesting
  void catchupAndFollow(SubscriptionRequest request, PgSynchronizedQuery query) {

    // we need to copy and preserve the current highwatermark **before** starting the query
    // in order not to lose facts by the ffTarget being updated after the phase 2 query, but
    // before the sending of the ffwd signal (#3722)
    HighWaterMark atTheStartOfQuery = ffwdTarget.highWaterMark();

    if (request.ephemeral()) {
      // just fast forward to the latest event published by now
      serial.set(fetcher.retrieveLatestSer());
    } else {
      catchup();
    }

    fastForward(atTheStartOfQuery);

    // propagate catchup
    if (isConnected()) {
      log.trace("{} signaling catchup", request);
      // signal catchup
      telemetry.onCatchup(this.request);
      pipeline.process(Signal.catchup());
    }
    if (isConnected()) {
      if (request.continuous()) {
        log.debug("{} entering follow mode", request);
        // signal follow
        telemetry.onFollow(this.request);
        long delayInMs;
        if (request.maxBatchDelayInMs() < 1) {
          // ok, instant query after NOTIFY
          delayInMs = 0;
        } else {
          // spread consumers, so that they query at different points
          // in time, even if they get triggered at the same PIT, and
          // share the same latency requirements
          //
          // ok, that is unlikely to be necessary, but easy to do,
          // so...
          delayInMs =
              ((request.maxBatchDelayInMs() / 4L) * 3L)
                  + (long) (Math.abs(Math.random() * (request.maxBatchDelayInMs() / 4.0)));
          log.trace(
              "{} setting delay to {}, maxDelay was {}",
              request,
              delayInMs,
              request.maxBatchDelayInMs());
        }
        condensedExecutor =
            new CondensedQueryExecutor(delayInMs, query, this::isConnected, request.specs());
        eventBus.register(condensedExecutor);
        // catchup phase 3 – make sure, we did not miss any fact due to
        // slow registration
        condensedExecutor.trigger();
      } else {
        pipeline.process(Signal.complete());
        log.debug("{} completed", request);
        // signal complete
        telemetry.onComplete(this.request);
      }
    }
  }

  @VisibleForTesting
  void fastForward(@NonNull HighWaterMark atTheStartOfQuery) {
    if (isConnected()) {

      UUID targetId = atTheStartOfQuery.targetId();
      long targetSer = atTheStartOfQuery.targetSer();

      // there is no need to check for the start id, as it'll be
      // contained in serial or smaller, see initializeSerialToStartAfter

      if (targetId != null && serial.get() < targetSer) {
        log.debug("{} sending ffwd to id {} (serial {})", request, targetId, targetSer);
        pipeline.process(Signal.of(FactStreamPosition.of(targetId, targetSer)));
      }
    }
  }

  @VisibleForTesting
  void catchup() {
    if (isConnected()) {
      log.trace("{} catchup phase1 - historic facts staring with SER={}", request, serial.get());
      pgCatchupFactory.create(request, pipeline, serial, statementHolder).run();
    }
    if (isConnected()) {
      log.trace("{} catchup phase2 - facts since connect (SER={})", request, serial.get());
      pgCatchupFactory.create(request, pipeline, serial, statementHolder).run();
    }
  }

  @VisibleForTesting
  boolean isConnected() {
    return !disconnected.get();
  }

  public synchronized void close() {
    log.trace("{} disconnecting ", request);
    disconnected.set(true);
    if (condensedExecutor != null) {
      eventBus.unregister(condensedExecutor);
      condensedExecutor.cancel();
      condensedExecutor = null;
    }
    statementHolder.close();
    log.debug("{} disconnected ", request);
    // signal close
    telemetry.onClose(request);
  }
}
