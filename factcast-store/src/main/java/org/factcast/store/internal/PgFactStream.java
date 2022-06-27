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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;

import org.factcast.core.Fact;
import org.factcast.core.subscription.FactStreamInfo;
import org.factcast.core.subscription.SubscriptionImpl;
import org.factcast.core.subscription.SubscriptionRequest;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.core.subscription.observer.FastForwardTarget;
import org.factcast.store.internal.blacklist.PgBlacklist;
import org.factcast.store.internal.catchup.PgCatchupFactory;
import org.factcast.store.internal.query.CurrentStatementHolder;
import org.factcast.store.internal.query.PgFactIdToSerialMapper;
import org.factcast.store.internal.query.PgLatestSerialFetcher;
import org.factcast.store.internal.query.PgQueryBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.RowCallbackHandler;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.eventbus.EventBus;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Creates and maintains a subscription.
 *
 * @author <uwe.schaefer@prisma-capacity.eu>
 */
@SuppressWarnings("UnstableApiUsage")
@Slf4j
@RequiredArgsConstructor
public class PgFactStream {

  final JdbcTemplate jdbcTemplate;

  final EventBus eventBus;

  final PgFactIdToSerialMapper idToSerMapper;

  final SubscriptionImpl subscription;

  @VisibleForTesting
  @Getter(AccessLevel.PROTECTED)
  final AtomicLong serial = new AtomicLong(0);

  final AtomicBoolean disconnected = new AtomicBoolean(false);

  final PgLatestSerialFetcher fetcher;

  final PgCatchupFactory pgCatchupFactory;
  final FastForwardTarget ffwdTarget;
  final PgMetrics metrics;
  final PgBlacklist blacklist;

  CondensedQueryExecutor condensedExecutor;

  @VisibleForTesting protected SubscriptionRequestTO request;

  PgPostQueryMatcher postQueryMatcher;
  final CurrentStatementHolder statementHolder = new CurrentStatementHolder();

  void connect(@NonNull SubscriptionRequestTO request) {
    this.request = request;
    log.debug("{} connect subscription {}", request, request.dump());
    postQueryMatcher = new PgPostQueryMatcher(request);
    PgQueryBuilder q = new PgQueryBuilder(request.specs(), statementHolder);
    initializeSerialToStartAfter();

    if (request.streamInfo()) {
      FactStreamInfo factStreamInfo = new FactStreamInfo(serial.get(), fetcher.retrieveLatestSer());
      subscription.notifyFactStreamInfo(factStreamInfo);
    }

    String sql = q.createSQL();
    PreparedStatementSetter setter = q.createStatementSetter(serial);
    RowCallbackHandler rsHandler =
        new FactRowCallbackHandler(
            subscription, postQueryMatcher, this::isConnected, serial, request, blacklist);
    PgSynchronizedQuery query =
        new PgSynchronizedQuery(jdbcTemplate, sql, setter, rsHandler, serial, fetcher);
    catchupAndFollow(request, subscription, query);
  }

  @VisibleForTesting
  void initializeSerialToStartAfter() {
    Optional<UUID> idRequestedToStartAfter = request.startingAfter();
    Long startingSerial = idRequestedToStartAfter.map(idToSerMapper::retrieve).orElse(0L);
    serial.set(startingSerial);
    log.trace("{} setting starting point to SER={}", request, startingSerial);
  }

  @VisibleForTesting
  void catchupAndFollow(
      SubscriptionRequest request, SubscriptionImpl subscription, PgSynchronizedQuery query) {
    if (request.ephemeral()) {
      // just fast forward to the latest event published by now
      serial.set(fetcher.retrieveLatestSer());
    } else {
      catchup(postQueryMatcher);
      logCatchupTransformationStats();
    }

    fastForward(request, subscription);

    // propagate catchup
    if (isConnected()) {
      log.trace("{} signaling catchup", request);
      subscription.notifyCatchup();
    }
    if (isConnected()) {
      if (request.continuous()) {
        log.debug("{} entering follow mode", request);
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
        subscription.notifyComplete();
        log.debug("{} completed", request);
      }
    }
  }

  @VisibleForTesting
  void fastForward(SubscriptionRequest request, SubscriptionImpl subscription) {
    if (isConnected()) {

      long startedSer = 0;
      UUID startedId = null;
      Optional<UUID> startingAfter = request.startingAfter();
      if (startingAfter.isPresent()) {
        startedId = startingAfter.get();
        startedSer = idToSerMapper.retrieve(startedId); // should be cached anyway
      }

      UUID targetId = ffwdTarget.targetId();
      long targetSer = ffwdTarget.targetSer();

      if (targetId != null && (targetSer > startedSer)) {
        subscription.notifyFastForward(targetId);
      }
    }
  }

  @VisibleForTesting
  void catchup(PgPostQueryMatcher postQueryMatcher) {
    if (isConnected()) {
      log.trace("{} catchup phase1 - historic facts staring with SER={}", request, serial.get());
      pgCatchupFactory
          .create(
              request, postQueryMatcher, subscription, serial, metrics, blacklist, statementHolder)
          .run();
    }
    if (isConnected()) {
      log.trace("{} catchup phase2 - facts since connect (SER={})", request, serial.get());
      pgCatchupFactory
          .create(
              request, postQueryMatcher, subscription, serial, metrics, blacklist, statementHolder)
          .run();
    }
  }

  @VisibleForTesting
  enum RatioLogLevel {
    DEBUG,
    INFO,
    WARN
  }

  private static final String ratioMessage = "{} CatchupTransformationRatio: {}%, ({}/{})";

  @VisibleForTesting
  void logCatchupTransformationStats() {
    if (subscription.factsTransformed().get() > 0) {
      long sum = subscription.factsTransformed().get() + subscription.factsNotTransformed().get();
      long transf = subscription.factsTransformed().get();
      long ratio = Math.round(100.0 / sum * transf);
      RatioLogLevel level = calculateLogLevel(sum, ratio);

      switch (level) {
        case DEBUG:
          log.debug(ratioMessage, request, ratio, transf, sum);
          break;
        case INFO:
          log.info(ratioMessage, request, ratio, transf, sum);
          break;
        case WARN:
          log.warn(ratioMessage, request, ratio, transf, sum);
          break;
        default:
          throw new IllegalArgumentException("switch fall-through. THIS IS A BUG! " + level);
      }
    }
  }

  @VisibleForTesting
  RatioLogLevel calculateLogLevel(long sum, long ratio) {
    // only bother sending metrics or raising the level if we did some significant catchup
    RatioLogLevel level = RatioLogLevel.DEBUG;
    if (sum >= 50) {
      metrics.distributionSummary(StoreMetrics.VALUE.CATCHUP_TRANSFORMATION_RATIO).record(ratio);

      if (ratio >= 20.0) {
        level = RatioLogLevel.WARN;
      } else if (ratio >= 10.0) {
        level = RatioLogLevel.INFO;
      }
    }
    return level;
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
  }

  @RequiredArgsConstructor
  static class FactRowCallbackHandler implements RowCallbackHandler {

    final SubscriptionImpl subscription;

    final PgPostQueryMatcher postQueryMatcher;

    final Supplier<Boolean> isConnectedSupplier;

    final AtomicLong serial;

    final SubscriptionRequestTO request;

    final PgBlacklist blacklist;

    @SuppressWarnings("NullableProblems")
    @Override
    public void processRow(ResultSet rs) throws SQLException {
      if (isConnectedSupplier.get()) {
        if (rs.isClosed()) {
          throw new IllegalStateException(
              "ResultSet already closed. We should not have got here. THIS IS A BUG!");
        }
        Fact f = PgFact.from(rs);
        UUID factId = f.id();
        var skipTesting = postQueryMatcher.canBeSkipped();

        try {
          if (blacklist.isBlocked(factId)) {
            log.trace("{} filtered blacklisted id={}", request, factId);
          } else {
            if (skipTesting || postQueryMatcher.test(f)) {
              subscription.notifyElement(f);
              log.trace("{} notifyElement called with id={}", request, factId);
            } else {
              log.trace("{} filtered id={}", request, factId);
            }
            serial.set(rs.getLong(PgConstants.COLUMN_SER));
          }
        } catch (Throwable e) {
          rs.close();
          subscription.notifyError(e);
        }
      }
    }
  }
}
