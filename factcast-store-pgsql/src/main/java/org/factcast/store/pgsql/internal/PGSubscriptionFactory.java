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
 * @author usr
 *
 */
// TODO integrate with PGQuery
@RequiredArgsConstructor
public class PGSubscriptionFactory {
	private final JdbcTemplate tpl;

	private final EventBus bus;
	private final PGFactIdToSerMapper serMapper;
	private final PGFactFactory factory;

	public Subscription subscribe(SubscriptionRequestTO req, FactStoreObserver observer) {
		PGQuery q = new PGQuery(tpl, bus, serMapper, factory);
		return q.catchup(req, observer);
	}

}
