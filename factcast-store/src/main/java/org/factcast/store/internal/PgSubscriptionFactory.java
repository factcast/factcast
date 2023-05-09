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
import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.SubscriptionImpl;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.core.subscription.TransformationException;
import org.factcast.core.subscription.observer.FactObserver;
import org.factcast.core.subscription.observer.FastForwardTarget;
import org.factcast.core.subscription.transformation.FactTransformerService;
import org.factcast.core.subscription.transformation.MissingTransformationInformationException;
import org.factcast.store.StoreConfigurationProperties;
import org.factcast.store.internal.catchup.PgCatchupFactory;
import org.factcast.store.internal.filter.blacklist.Blacklist;
import org.factcast.store.internal.query.PgFactIdToSerialMapper;
import org.factcast.store.internal.query.PgLatestSerialFetcher;
import org.factcast.store.internal.script.JSEngineFactory;
import org.springframework.jdbc.core.JdbcTemplate;

// TODO integrate with PGQuery
@SuppressWarnings("UnstableApiUsage")
@Slf4j
class PgSubscriptionFactory implements AutoCloseable {

  final JdbcTemplate jdbcTemplate;

  final EventBus eventBus;

  final PgFactIdToSerialMapper idToSerialMapper;

  final PgLatestSerialFetcher fetcher;

  final PgCatchupFactory catchupFactory;

  final FastForwardTarget target;
  final PgMetrics metrics;
  final Blacklist blacklist;
  final FactTransformerService transformerService;
  final JSEngineFactory ef;

  private final ExecutorService es;

  public PgSubscriptionFactory(
      JdbcTemplate jdbcTemplate,
      EventBus eventBus,
      PgFactIdToSerialMapper idToSerialMapper,
      PgLatestSerialFetcher fetcher,
      StoreConfigurationProperties props,
      PgCatchupFactory catchupFactory,
      FastForwardTarget target,
      PgMetrics metrics,
      Blacklist blacklist,
      FactTransformerService transformerService,
      JSEngineFactory ef) {
    this.jdbcTemplate = jdbcTemplate;
    this.eventBus = eventBus;
    this.idToSerialMapper = idToSerialMapper;
    this.fetcher = fetcher;
    this.catchupFactory = catchupFactory;
    this.target = target;
    this.metrics = metrics;
    this.blacklist = blacklist;
    this.transformerService = transformerService;
    this.ef = ef;
    this.es =
        metrics.monitor(
            Executors.newFixedThreadPool(props.getSizeOfThreadPoolForSubscriptions()),
            "subscription-factory");
  }

  public Subscription subscribe(SubscriptionRequestTO req, FactObserver observer) {
    SubscriptionImpl subscription = SubscriptionImpl.on(observer);
    PgFactStream pgsub =
        new PgFactStream(
            jdbcTemplate,
            eventBus,
            idToSerialMapper,
            subscription,
            fetcher,
            catchupFactory,
            target,
            transformerService,
            blacklist,
            metrics,
            ef);

    // when closing the subscription, also close the PgFactStream
    subscription.onClose(pgsub::close);
    CompletableFuture.runAsync(connect(req, subscription, pgsub), es);

    return subscription;
  }

  @NonNull
  @VisibleForTesting
  Runnable connect(SubscriptionRequestTO req, SubscriptionImpl subscription, PgFactStream pgsub) {
    return () -> {
      try {
        pgsub.connect(req);
      } catch (MissingTransformationInformationException e) {
        // warn level because it hints at broken transformations/schema registry
        warnAndNotify(subscription, req, "missing transformation", e);
      } catch (TransformationException e) {
        errorAndNotify(subscription, req, "failing transformation", e);
      } catch (Exception e) {
        // warn level because it is unexpected and unlikely to be a client induced error
        // not limiting to RuntimeException, in case anyone used @SneakyThrows
        warnAndNotify(subscription, req, "runtime", e);
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
