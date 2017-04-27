package org.factcast.store.pgsql.internal;

import org.factcast.core.subscription.FactStoreObserver;
import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.SubscriptionRequestTO;
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
    private final JdbcTemplate jdbcTemplate;

    private final EventBus eventBus;

    private final PGFactIdToSerMapper idToSerialMapper;

    public Subscription subscribe(SubscriptionRequestTO req, FactStoreObserver observer) {
        PGSubscription subscription = new PGSubscription(jdbcTemplate, eventBus, idToSerialMapper);
        subscription.run(req, observer);
        return subscription;
    }

}
