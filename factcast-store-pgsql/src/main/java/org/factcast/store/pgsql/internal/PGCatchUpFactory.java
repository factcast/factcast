package org.factcast.store.pgsql.internal;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.factcast.core.Fact;
import org.factcast.core.subscription.SubscriptionImpl;
import org.factcast.core.subscription.SubscriptionRequestTO;
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
    static class PGCatchup implements AutoCloseable, Runnable {
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

        private long clientId = 0;

        final AtomicLong serial;

        @Override
        public synchronized void close() {
            if (clientId > 0) {
                jdbc.update(PGConstants.DELETE_CATCH_BY_CID, clientId);
            }
        }

        @Override
        public void run() {
            PGCatchupPrepare prep = new PGCatchupPrepare(jdbc, request);
            clientId = prep.prepareCatchup(serial);
            PGCatchupFetchPage fetch = new PGCatchupFetchPage(jdbc, clientId, props.getFetchSize());

            while (true) {
                List<Fact> fetchFacts = fetch.fetchFacts(serial);
                if (fetchFacts.isEmpty()) {
                    break;
                }

                fetchFacts.forEach(f -> {
                    UUID factId = f.id();

                    if (postQueryMatcher.test(f)) {
                        try {
                            subscription.notifyElement(f);
                            log.trace("{} notifyElement called with id={}", request, factId);
                        } catch (Throwable e) {
                            // debug level, because it happens regularly on
                            // disconnecting clients.
                            log.debug("{} exception from subscription: {}", request, e
                                    .getMessage());

                            try {
                                subscription.close();
                            } catch (Exception e1) {
                                log.warn("{} exception while closing subscription: {}", request, e1
                                        .getMessage());
                            }
                            throw e;
                        }
                    } else {
                        log.trace("{} filtered id={}", request, factId);
                    }
                });
            }

            // just in case
            close();
        }

    }

}
