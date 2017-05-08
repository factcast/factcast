package org.factcast.store.pgsql.internal;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.factcast.core.Fact;
import org.factcast.core.subscription.SubscriptionImpl;
import org.factcast.core.subscription.SubscriptionRequest;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.RowCallbackHandler;

import com.google.common.eventbus.EventBus;
import com.impossibl.postgres.jdbc.PGSQLSimpleException;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// TODO split
// TODO document properly
@Slf4j
@RequiredArgsConstructor
// TODO needs new name
class PGFactStream {

    final JdbcTemplate jdbcTemplate;

    final EventBus eventBus;

    final PGFactIdToSerMapper idToSerMapper;

    final SubscriptionImpl<Fact> subscription;

    final AtomicLong serial = new AtomicLong(0);

    final AtomicBoolean disconnected = new AtomicBoolean(false);

    final PGLatestSerialFetcher fetcher;

    CondensedQueryExecutor condensedExecutor;

    SubscriptionRequestTO request;

    void connect(@NonNull SubscriptionRequestTO request) {

        this.request = request;
        log.debug("{} connecting subscription {}", request, request.dump());

        PGQueryBuilder q = new PGQueryBuilder(request);

        initializeSerialToStartAfter();

        String sql = q.createSQL();
        PreparedStatementSetter setter = q.createStatementSetter(serial);
        RowCallbackHandler rsHandler = new FactRowCallbackHandler(subscription,
                new PGPostQueryMatcher(request));

        PGSynchronizedQuery query = new PGSynchronizedQuery(jdbcTemplate, sql, setter, rsHandler,
                serial, fetcher);
        catchupAndFollow(request, subscription, query);
    }

    private void initializeSerialToStartAfter() {
        Long startingSerial = request.startingAfter().map(idToSerMapper::retrieve).orElse(0L);
        serial.set(startingSerial);
        log.trace("{} setting starting point to SER={}", request, startingSerial);
    }

    private void catchupAndFollow(SubscriptionRequest request, SubscriptionImpl<Fact> subscription,
            PGSynchronizedQuery query) {

        if (request.ephemeral()) {
            // just fast forward to the latest event publish by now
            this.serial.set(fetcher.retrieveLatestSer());
        } else {
            catchup(query);
        }

        // propagate catchup
        if (isConnected()) {
            log.trace("{} signaling catchup", request);
            subscription.notifyCatchup();
        }

        if (isConnected() && request.continous()) {

            log.info("{} entering follow mode", request);

            long delayInMs;

            if (request.maxBatchDelayInMs() < 1) {
                // ok, instant query after NOTIFY
                delayInMs = 0;
            } else {
                // spread consumers, so that they query at different points in
                // time, even if they get triggered at the same PIT, and share
                // the same latency requirements
                //
                // ok, that is unlikely to be necessary, but easy to do, so...
                delayInMs = ((request.maxBatchDelayInMs() / 4L) * 3L) + (long) (Math.abs(Math
                        .random() * (request.maxBatchDelayInMs() / 4.0)));
                log.info("{} setting delay to {}, maxDelay was {}", request, delayInMs, request
                        .maxBatchDelayInMs());
            }

            this.condensedExecutor = new CondensedQueryExecutor(delayInMs, query,
                    () -> isConnected());
            eventBus.register(condensedExecutor);
            // catchup phase 3 – make sure, we did not miss any fact due to
            // slow registration
            condensedExecutor.trigger();

        } else {

            subscription.notifyComplete();
            log.debug("Completed {}", request);
            // FIXME disc.?

        }
    }

    private void catchup(PGSynchronizedQuery query) {
        if (isConnected()) {
            log.trace("{} catchup phase1 - historic facts staring with SER={}", request, serial
                    .get());
            query.run(true);
        }
        if (isConnected()) {
            log.trace("{} catchup phase2 - facts since connect (SER={})", request, serial.get());
            query.run(true);
        }
    }

    private boolean isConnected() {
        return !disconnected.get();
    }

    public void close() {
        log.debug("{} disconnecting ", request);
        disconnected.set(true);

        if (condensedExecutor != null) {
            eventBus.unregister(condensedExecutor);
            condensedExecutor.cancel();
        }

        log.info("{} disconnected ", request);
    }

    @RequiredArgsConstructor
    private class FactRowCallbackHandler implements RowCallbackHandler {

        final SubscriptionImpl<Fact> subscription;

        final PGPostQueryMatcher postQueryMatcher;

        @Override
        public void processRow(ResultSet rs) throws SQLException {
            if (isConnected()) {

                if (rs.isClosed()) {
                    throw new PGSQLSimpleException(
                            "ResultSet already closed. We should not have got here. THIS IS A BUG!");
                }

                Fact f = PGFact.from(rs);
                final UUID factId = f.id();

                if (postQueryMatcher.test(f)) {
                    try {
                        subscription.notifyElement(f);
                        log.trace("{} onNext called with id={}", request, factId);
                    } catch (Throwable e) {
                        // debug level, because it happens regularly on
                        // disconnecting clients.
                        // TODO add sid
                        log.debug("{} exception from subscription: {}", request, e.getMessage());

                        try {
                            subscription.close();
                        } catch (Exception e1) {
                            // TODO add sid
                            log.warn("{} exception while closing subscription: {}", request, e1
                                    .getMessage());
                        }

                        // close result set in order to release DB resources as
                        // early as possible
                        rs.close();

                        throw e;

                    }
                } else {
                    // TODO add sid
                    log.trace("{} filtered id={}", request, factId);
                }
                serial.set(rs.getLong(PGConstants.COLUMN_SER));
            }
        }

    }
}
