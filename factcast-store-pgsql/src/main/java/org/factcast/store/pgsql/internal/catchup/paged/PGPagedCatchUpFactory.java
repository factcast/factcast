package org.factcast.store.pgsql.internal.catchup.paged;

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
public class PGPagedCatchUpFactory implements PGCatchupFactory {

    final JdbcTemplate jdbc;

    final PGConfigurationProperties props;

    final PGFactIdToSerialMapper serMapper;

    public PGPagedCatchup create(

            @NonNull SubscriptionRequestTO request, @NonNull PGPostQueryMatcher postQueryMatcher,
            @NonNull SubscriptionImpl<Fact> subscription, @NonNull AtomicLong serial) {

        return new PGPagedCatchup(jdbc, props, serMapper, request, postQueryMatcher, subscription,
                serial);
    }

}
