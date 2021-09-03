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

import java.util.concurrent.CompletableFuture;

import org.factcast.core.subscription.FactTransformersFactory;
import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.SubscriptionImpl;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.core.subscription.TransformationException;
import org.factcast.core.subscription.observer.FactObserver;
import org.factcast.core.subscription.observer.FastForwardTarget;
import org.factcast.store.internal.catchup.PgCatchupFactory;
import org.factcast.store.internal.query.PgFactIdToSerialMapper;
import org.factcast.store.internal.query.PgLatestSerialFetcher;
import org.factcast.store.registry.transformation.chains.MissingTransformationInformation;
import org.springframework.jdbc.core.JdbcTemplate;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.eventbus.EventBus;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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

  public Subscription subscribe(SubscriptionRequestTO req, FactObserver observer) {
    SubscriptionImpl subscription = SubscriptionImpl.on(observer);
    PgFactStream pgsub =
        new PgFactStream(
            jdbcTemplate,
            eventBus,
            idToSerialMapper,
            subscription,
            transformersFactory,
            fetcher,
            catchupFactory,
            target,
            metrics);

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
      } catch (MissingTransformationInformation | TransformationException e) {
        log.warn("{} transformation error: {}", req, e.getMessage());
        subscription.notifyError(e);
      } catch (Throwable e) {
        subscription.notifyError(e);
      }
    };
  }
}
