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
import java.util.concurrent.*;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.subscription.*;
import org.factcast.core.subscription.MissingTransformationInformationException;
import org.factcast.core.subscription.observer.*;
import org.factcast.store.StoreConfigurationProperties;
import org.factcast.store.internal.catchup.PgCatchupFactory;
import org.factcast.store.internal.listen.PgConnectionSupplier;
import org.factcast.store.internal.pipeline.*;
import org.factcast.store.internal.query.*;
import org.factcast.store.internal.script.JSEngineFactory;
import org.factcast.store.internal.telemetry.PgStoreTelemetry;

// TODO integrate with PGQuery
@SuppressWarnings("UnstableApiUsage")
@Slf4j
public class PgSubscriptionFactory implements AutoCloseable {

  final PgConnectionSupplier connectionSupplier;

  final EventBus eventBus;

  final PgFactIdToSerialMapper idToSerialMapper;

  final PgCatchupFactory catchupFactory;

  final HighWaterMarkFetcher hwmFetcher;
  final ServerPipelineFactory pipelineFactory;
  final JSEngineFactory jsEngineFactory;
  final ExecutorService es;
  final PgStoreTelemetry telemetry;
  private final int maxPipelineBufferSize;

  public PgSubscriptionFactory(
      PgConnectionSupplier connectionSupplier,
      EventBus eventBus,
      PgFactIdToSerialMapper idToSerialMapper,
      StoreConfigurationProperties props,
      PgCatchupFactory catchupFactory,
      HighWaterMarkFetcher hwmFetcher,
      ServerPipelineFactory pipelineFactory,
      JSEngineFactory jsEngineFactory,
      PgMetrics metrics,
      PgStoreTelemetry telemetry) {
    this.connectionSupplier = connectionSupplier;
    this.eventBus = eventBus;
    this.idToSerialMapper = idToSerialMapper;
    this.catchupFactory = catchupFactory;
    this.hwmFetcher = hwmFetcher;
    this.pipelineFactory = pipelineFactory;
    this.jsEngineFactory = jsEngineFactory;
    this.telemetry = telemetry;

    this.maxPipelineBufferSize = props.getTransformationCachePageSize();

    this.es =
        metrics.monitor(
            Executors.newFixedThreadPool(props.getSizeOfThreadPoolForSubscriptions()),
            "subscription-factory");
  }

  public Subscription subscribe(SubscriptionRequestTO req, FactObserver observer) {
    SubscriptionImpl subscription = SubscriptionImpl.on(observer);

    ServerPipeline pipe = pipelineFactory.create(req, subscription, maxPipelineBufferSize);

    PgFactStream pgsub =
        new PgFactStream(
            connectionSupplier,
            eventBus,
            idToSerialMapper,
            catchupFactory,
            hwmFetcher,
            pipe,
            telemetry,
            req);

    // when closing the subscription, also close the PgFactStream
    subscription.onClose(pgsub::close);
    CompletableFuture.runAsync(connect(subscription, pgsub), es);

    return subscription;
  }

  @NonNull
  @VisibleForTesting
  Runnable connect(SubscriptionImpl subscription, PgFactStream pgSub) {
    return () -> {
      try {
        pgSub.connect();
      } catch (MissingTransformationInformationException e) {
        // warn level because it hints at broken transformations/schema registry
        warnAndNotify(subscription, pgSub.request(), "missing transformation", e);
      } catch (TransformationException e) {
        errorAndNotify(subscription, pgSub.request(), "failing transformation", e);
      } catch (Exception e) {
        // warn level because it is unexpected and unlikely to be a client induced error
        // not limiting to RuntimeException, in case anyone used @SneakyThrows
        warnAndNotify(subscription, pgSub.request(), "runtime", e);
      }
    };
  }

  private static final String LOGLINE = "{} Notifying subscriber of {} error: {}";

  @VisibleForTesting
  void warnAndNotify(
      @NonNull SubscriptionImpl sub,
      @NonNull SubscriptionRequestTO req,
      @NonNull String typeOfError,
      @NonNull Exception e) {
    log.warn(LOGLINE, req, typeOfError, e.getMessage());
    sub.notifyError(e);
  }

  @VisibleForTesting
  void errorAndNotify(
      @NonNull SubscriptionImpl sub,
      @NonNull SubscriptionRequestTO req,
      @NonNull String typeOfError,
      @NonNull Exception e) {
    log.error(LOGLINE, req, typeOfError, e.getMessage());
    sub.notifyError(e);
  }

  @Override
  public void close() throws Exception {
    es.shutdown();
    es.awaitTermination(2, TimeUnit.SECONDS);
  }
}
