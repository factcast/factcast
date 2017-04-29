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

    final PGFilteringStats stats = new PGFilteringStats();

    private CondensedQueryExecutor condensedExecutor;

    void connect(SubscriptionRequestTO request) {
        log.trace("initializing for {}", request);

        PGQueryBuilder q = new PGQueryBuilder(request);

        initializeSerialToStartAfter(request);

        String sql = q.createSQL();
        PreparedStatementSetter setter = q.createStatementSetter(serial);
        RowCallbackHandler rsHandler = new FactRowCallbackHandler(subscription,
                new PGPostQueryMatcher(request.specs()));

        PGSynchronizedQuery query = new PGSynchronizedQuery(jdbcTemplate, sql, setter, rsHandler);
        catchupAndFollow(request, subscription, query);
    }

    private void initializeSerialToStartAfter(SubscriptionRequestTO request) {
        Long startingSerial = request.startingAfter().map(idToSerMapper::retrieve).orElse(0L);
        serial.set(startingSerial);
        log.trace("starting to stream from id: {}", startingSerial);
    }

    private void catchupAndFollow(SubscriptionRequest request, SubscriptionImpl<Fact> subscription,
            PGSynchronizedQuery query) {

        stats.reset();

        if (request.ephemeral()) {
            // just fast forward to the latest event publish by now
            this.serial.set(getLatestFactSer());
        } else {
            catchup(query);
        }

        // propagate catchup
        if (isConnected()) {
            log.trace("signaling catchup");
            subscription.notifyCatchup();
            stats.dumpForCatchup();
        }

        if (isConnected() && request.continous()) {

            log.info("Entering follow mode for {}", request);
            stats.reset();

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
                delayInMs = (((request.maxBatchDelayInMs() / 4L) * 3L) + (long) (Math.abs(Math
                        .random() * ((request.maxBatchDelayInMs() / 4)))));
                log.info("Setting delay for this instance to " + delayInMs + ", maxDelay was "
                        + request.maxBatchDelayInMs());
            }

            this.condensedExecutor = new CondensedQueryExecutor(delayInMs, query,
                    () -> isConnected());
            eventBus.register(condensedExecutor);
            // catchup phase 3 – make sure, we did not miss any fact due to
            // slow registration
            condensedExecutor.trigger();

        } else {
            log.debug("Complete");
            subscription.notifyComplete();
            // FIXME disc.?

        }
    }

    private void catchup(PGSynchronizedQuery query) {
        if (isConnected()) {
            log.trace("catchup phase1 - historic Facts");
            query.run(true);
        }
        if (isConnected()) {
            log.trace("catchup phase2 - Facts since connect");
            query.run(true);
        }
    }

    private boolean isConnected() {
        return !disconnected.get();
    }

    private long getLatestFactSer() {
        return jdbcTemplate.queryForObject(PGConstants.SELECT_LATEST_SER, Long.class).longValue();
    }

    public void close() {
        log.info("Disconnecting");
        disconnected.set(true);

        if (condensedExecutor != null) {
            eventBus.unregister(condensedExecutor);
            condensedExecutor.cancel();
        }

        stats.dump();
        log.info("Disconnected");
    }

    @RequiredArgsConstructor
    private class FactRowCallbackHandler implements RowCallbackHandler {

        final SubscriptionImpl<Fact> subscription;

        final PGPostQueryMatcher postQueryMatcher;

        @Override
        public void processRow(ResultSet rs) throws SQLException {
            if (isConnected()) {

                if (rs.isClosed()) {
                    throw new RuntimeException(
                            "ResultSet already close. We should not have got here. THIS IS A BUG!");
                }

                Fact f = PGFact.from(rs);
                stats.notifyCount();
                final UUID factId = f.id();

                if (postQueryMatcher.test(f)) {
                    stats.notifyHit();
                    try {
                        subscription.notifyElement(f);
                        log.trace("onNext called with id={}", factId);
                    } catch (Throwable e) {
                        // debug level, because it happens regularly on
                        // disconnecting clients.
                        log.debug("Exception from subscription: {}", e.getMessage());

                        try {
                            subscription.close();
                        } catch (Exception e1) {
                            log.warn("Exception while closing subscription: {}", e1.getMessage());
                        }

                        // close result set in order to release DB resources as
                        // early as possible
                        rs.close();

                        throw e;

                    }
                } else {
                    log.trace("filtered id={}", factId);
                }
                serial.set(rs.getLong(PGConstants.COLUMN_SER));
            }
        }

    }
}
