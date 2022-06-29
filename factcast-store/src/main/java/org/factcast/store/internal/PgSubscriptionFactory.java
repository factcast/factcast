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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.subscription.*;
import org.factcast.core.subscription.observer.FactObserver;
import org.factcast.core.subscription.observer.FastForwardTarget;
import org.factcast.store.internal.catchup.PgCatchupFactory;
import org.factcast.store.internal.filter.PgBlacklist;
import org.factcast.store.internal.query.PgFactIdToSerialMapper;
import org.factcast.store.internal.query.PgLatestSerialFetcher;
import org.springframework.jdbc.core.JdbcTemplate;

// TODO integrate with PGQuery
@SuppressWarnings("UnstableApiUsage")
@RequiredArgsConstructor
@Slf4j
class PgSubscriptionFactory {

  final JdbcTemplate jdbcTemplate;

  final EventBus eventBus;

  final PgFactIdToSerialMapper idToSerialMapper;

  final PgLatestSerialFetcher fetcher;

  final PgCatchupFactory catchupFactory;

  final FactTransformersFactory transformersFactory;
  final FastForwardTarget target;
  final PgMetrics metrics;
  final PgBlacklist blacklist;

  public Subscription subscribe(SubscriptionRequestTO req, FactObserver observer) {
    SubscriptionImpl subscription =
        SubscriptionImpl.on(observer, transformersFactory.createFor(req));
    PgFactStream pgsub =
        new PgFactStream(
            jdbcTemplate,
            eventBus,
            idToSerialMapper,
            subscription,
            fetcher,
            catchupFactory,
            target,
            metrics,
            blacklist);

    // when closing the subscription, also close the PgFactStream
    subscription.onClose(pgsub::close);
    CompletableFuture.runAsync(connect(req, subscription, pgsub));

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
      } catch (RuntimeException e) {
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
}
