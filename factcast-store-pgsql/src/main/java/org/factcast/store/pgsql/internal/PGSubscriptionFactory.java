package org.factcast.store.pgsql.internal;

import java.util.concurrent.CompletableFuture;

import org.factcast.core.Fact;
import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.SubscriptionImpl;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.core.subscription.observer.FactObserver;
import org.springframework.jdbc.core.JdbcTemplate;

import com.google.common.eventbus.EventBus;

import lombok.RequiredArgsConstructor;

/**
 * Creates Subscription
 * 
 * @author uwe.schaefer@mercateo.com
 *
 */
// TODO integrate with PGQuery
@RequiredArgsConstructor
class PGSubscriptionFactory {
    final JdbcTemplate jdbcTemplate;

    final EventBus eventBus;

    final PGFactIdToSerMapper idToSerialMapper;

    final PGLatestSerialFetcher fetcher;

    public Subscription subscribe(SubscriptionRequestTO req, FactObserver observer) {
        final SubscriptionImpl<Fact> subscription = SubscriptionImpl.on(observer);

        PGFactStream pgsub = new PGFactStream(jdbcTemplate, eventBus, idToSerialMapper,
                subscription, fetcher);
        CompletableFuture.runAsync(() -> pgsub.connect(req));

        return subscription.onClose(pgsub::close);
    }

}
