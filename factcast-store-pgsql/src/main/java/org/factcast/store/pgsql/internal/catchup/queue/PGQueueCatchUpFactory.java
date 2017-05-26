package org.factcast.store.pgsql.internal.catchup.queue;

import java.util.concurrent.atomic.AtomicLong;

import org.factcast.core.Fact;
import org.factcast.core.subscription.SubscriptionImpl;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.store.pgsql.PGConfigurationProperties;
import org.factcast.store.pgsql.internal.PGPostQueryMatcher;
import org.factcast.store.pgsql.internal.catchup.PGCatchupFactory;
import org.factcast.store.pgsql.internal.query.PGFactIdToSerialMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class PGQueueCatchUpFactory implements PGCatchupFactory {

    final JdbcTemplate jdbc;

    final PGConfigurationProperties props;

    final PGFactIdToSerialMapper serMapper;

    public PGQueueCatchup create(

            @NonNull SubscriptionRequestTO request, @NonNull PGPostQueryMatcher postQueryMatcher,
            @NonNull SubscriptionImpl<Fact> subscription, @NonNull AtomicLong serial) {

        return new PGQueueCatchup(jdbc, props, serMapper, request, postQueryMatcher, subscription,
                serial);
    }

}
