/*
 * Copyright © 2017-2020 factcast.org
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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.factcast.core.Fact;
import org.factcast.core.subscription.SubscriptionImpl;
import org.factcast.core.subscription.SubscriptionRequest;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.store.pgsql.internal.catchup.PgCatchupFactory;
import org.factcast.store.pgsql.internal.query.PgFactIdToSerialMapper;
import org.factcast.store.pgsql.internal.query.PgLatestSerialFetcher;
import org.factcast.store.pgsql.internal.query.PgQueryBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.RowCallbackHandler;

import com.google.common.eventbus.EventBus;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Creates and maintains a subscription.
 *
 * @author <uwe.schaefer@mercateo.com>
 */
@SuppressWarnings("UnstableApiUsage")
@Slf4j
@RequiredArgsConstructor
public class PgFactStream {

    final JdbcTemplate jdbcTemplate;

    final EventBus eventBus;

    final PgFactIdToSerialMapper idToSerMapper;

    final SubscriptionImpl<Fact> subscription;

    final AtomicLong serial = new AtomicLong(0);

    final AtomicBoolean disconnected = new AtomicBoolean(false);

    final PgLatestSerialFetcher fetcher;

    final PgCatchupFactory pgCatchupFactory;

    CondensedQueryExecutor condensedExecutor;

    SubscriptionRequestTO request;

    PgPostQueryMatcher postQueryMatcher;

    void connect(@NonNull SubscriptionRequestTO request) {
        this.request = request;
        log.debug("{} connecting subscription {}", request, request.dump());
        postQueryMatcher = new PgPostQueryMatcher(request);
        PgQueryBuilder q = new PgQueryBuilder(request);
        initializeSerialToStartAfter();
        String sql = q.createSQL();
        PreparedStatementSetter setter = q.createStatementSetter(serial);
        RowCallbackHandler rsHandler = new FactRowCallbackHandler(subscription, postQueryMatcher);
        PgSynchronizedQuery query = new PgSynchronizedQuery(jdbcTemplate, sql, setter, rsHandler,
                serial, fetcher);
        catchupAndFollow(request, subscription, query);
    }

    private void initializeSerialToStartAfter() {
        Long startingSerial = request.startingAfter().map(idToSerMapper::retrieve).orElse(0L);
        serial.set(startingSerial);
        log.trace("{} setting starting point to SER={}", request, startingSerial);
    }

    private void catchupAndFollow(SubscriptionRequest request, SubscriptionImpl<Fact> subscription,
            PgSynchronizedQuery query) {
        if (request.ephemeral()) {
            // just fast forward to the latest event publish by now
            this.serial.set(fetcher.retrieveLatestSer());
        } else {
            catchup(postQueryMatcher);
        }
        // propagate catchup
        if (isConnected()) {
            log.trace("{} signaling catchup", request);
            subscription.notifyCatchup();
        }
        if (isConnected() && request.continuous()) {
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
                delayInMs = ((request.maxBatchDelayInMs() / 4L) * 3L)
                        + (long) (Math.abs(Math.random() * (request.maxBatchDelayInMs() / 4.0)));
                log.info("{} setting delay to {}, maxDelay was {}", request, delayInMs, request
                        .maxBatchDelayInMs());
            }
            condensedExecutor = new CondensedQueryExecutor(delayInMs, query, this::isConnected);
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

    private void catchup(PgPostQueryMatcher postQueryMatcher) {
        if (isConnected()) {
            log.debug("{} catchup phase1 - historic facts staring with SER={}", request, serial
                    .get());
            pgCatchupFactory.create(request, postQueryMatcher, subscription, serial).run();
        }
        if (isConnected()) {
            log.debug("{} catchup phase2 - facts since connect (SER={})", request, serial.get());
            pgCatchupFactory.create(request, postQueryMatcher, subscription, serial).run();
        }
    }

    private boolean isConnected() {
        return !disconnected.get();
    }

    public synchronized void close() {
        log.debug("{} disconnecting ", request);
        disconnected.set(true);
        if (condensedExecutor != null) {
            eventBus.unregister(condensedExecutor);
            condensedExecutor.cancel();
            condensedExecutor = null;
        }
        log.info("{} disconnected ", request);
    }

    @RequiredArgsConstructor
    private class FactRowCallbackHandler implements RowCallbackHandler {

        final SubscriptionImpl<Fact> subscription;

        final PgPostQueryMatcher postQueryMatcher;

        @SuppressWarnings("NullableProblems")
        @Override
        public void processRow(ResultSet rs) throws SQLException {
            if (isConnected()) {
                if (rs.isClosed()) {
                    throw new IllegalStateException(
                            "ResultSet already closed. We should not have got here. THIS IS A BUG!");
                }
                Fact f = PgFact.from(rs);
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
                serial.set(rs.getLong(PgConstants.COLUMN_SER));
            }
        }
    }
}
