package org.factcast.store.pgsql.internal.catchup;

import java.util.LinkedList;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.factcast.core.Fact;
import org.factcast.core.subscription.SubscriptionImpl;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.store.pgsql.internal.PGConfigurationProperties;
import org.factcast.store.pgsql.internal.PGConstants;
import org.factcast.store.pgsql.internal.PGFactIdToSerMapper;
import org.factcast.store.pgsql.internal.PGPostQueryMatcher;
import org.springframework.jdbc.core.JdbcTemplate;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class PGCatchUpFactory {

    final JdbcTemplate jdbc;

    final PGConfigurationProperties props;

    final PGFactIdToSerMapper serMapper;

    public PGCatchup create(@NonNull SubscriptionRequestTO request,
            PGPostQueryMatcher postQueryMatcher, SubscriptionImpl<Fact> subscription,
            AtomicLong serial) {
        return new PGCatchup(jdbc, props, serMapper, request, postQueryMatcher, subscription,
                serial);
    }

    @RequiredArgsConstructor
    public static class PGCatchup implements Runnable {
        @NonNull
        final JdbcTemplate jdbc;

        @NonNull
        final PGConfigurationProperties props;

        @NonNull
        final PGFactIdToSerMapper serMapper;

        @NonNull
        final SubscriptionRequestTO request;

        @NonNull
        final PGPostQueryMatcher postQueryMatcher;

        @NonNull
        final SubscriptionImpl<Fact> subscription;

        @NonNull
        final AtomicLong serial;

        private long clientId = 0;

        @Override
        public void run() {
            PGCatchUpPrepare prep = new PGCatchUpPrepare(jdbc, request);
            clientId = prep.prepareCatchup(serial);

            if (clientId > 0) {
                try {
                    PGCatchUpFetchPage fetch = new PGCatchUpFetchPage(jdbc, request, clientId, props
                            .getFetchSize());

                    while (true) {
                        LinkedList<Fact> facts = fetch.fetchFacts(serial);
                        if (facts.isEmpty()) {
                            // we have reached the end
                            break;
                        }

                        while (!facts.isEmpty()) {
                            Fact f = facts.removeFirst();
                            UUID factId = f.id();

                            if (postQueryMatcher.test(f)) {
                                try {
                                    subscription.notifyElement(f);
                                    log.trace("{} notifyElement called with id={}", request,
                                            factId);
                                } catch (Throwable e) {
                                    // debug level, because it happens regularly
                                    // on
                                    // disconnecting clients.
                                    log.debug("{} exception from subscription: {}", request, e
                                            .getMessage());

                                    try {
                                        subscription.close();
                                    } catch (Exception e1) {
                                        log.warn("{} exception while closing subscription: {}",
                                                request, e1.getMessage());
                                    }
                                    throw e;
                                }
                            } else {
                                log.trace("{} filtered id={}", request, factId);
                            }
                        }
                    }
                } finally {
                    jdbc.update(PGConstants.DELETE_CATCH_BY_CID, clientId);
                }
            }
        }

    }

}
