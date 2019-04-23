/*
 * Copyright © 2018 Mercateo AG (http://www.mercateo.com)
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
package org.factcast.store.pgsql.internal;

import java.util.concurrent.CompletableFuture;

import org.factcast.core.Fact;
import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.SubscriptionImpl;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.core.subscription.observer.FactObserver;
import org.factcast.store.pgsql.internal.catchup.PgCatchupFactory;
import org.factcast.store.pgsql.internal.query.PgFactIdToSerialMapper;
import org.factcast.store.pgsql.internal.query.PgLatestSerialFetcher;
import org.springframework.jdbc.core.JdbcTemplate;

import com.google.common.eventbus.EventBus;

import lombok.RequiredArgsConstructor;

// TODO integrate with PGQuery
@SuppressWarnings("UnstableApiUsage")
@RequiredArgsConstructor
class PgSubscriptionFactory {

    final JdbcTemplate jdbcTemplate;

    final EventBus eventBus;

    final PgFactIdToSerialMapper idToSerialMapper;

    final PgLatestSerialFetcher fetcher;

    final PgCatchupFactory catchupFactory;

    public Subscription subscribe(SubscriptionRequestTO req, FactObserver observer) {
        final SubscriptionImpl<Fact> subscription = SubscriptionImpl.on(observer);
        PgFactStream pgsub = new PgFactStream(jdbcTemplate, eventBus, idToSerialMapper,
                subscription, fetcher, catchupFactory);
        CompletableFuture.runAsync(() -> pgsub.connect(req));
        return subscription.onClose(pgsub::close);
    }
}
