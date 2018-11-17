/**
 * Copyright © 2018 Mercateo AG (http://www.mercateo.com)
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
package org.factcast.store.pgsql.internal.catchup.queue;

import java.util.LinkedList;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.factcast.core.Fact;
import org.factcast.core.subscription.SubscriptionCancelledException;
import org.factcast.core.subscription.SubscriptionImpl;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.store.pgsql.PGConfigurationProperties;
import org.factcast.store.pgsql.internal.PGConstants;
import org.factcast.store.pgsql.internal.PGPostQueryMatcher;
import org.factcast.store.pgsql.internal.catchup.PGCatchUpFetchPage;
import org.factcast.store.pgsql.internal.catchup.PGCatchUpPrepare;
import org.factcast.store.pgsql.internal.catchup.PGCatchup;
import org.factcast.store.pgsql.internal.query.PGFactIdToSerialMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
public class PGQueueCatchup implements PGCatchup {

    @NonNull
    final JdbcTemplate jdbc;

    @NonNull
    final PGConfigurationProperties props;

    @NonNull
    final PGFactIdToSerialMapper serMapper;

    @NonNull
    final SubscriptionRequestTO request;

    @NonNull
    final PGPostQueryMatcher postQueryMatcher;

    @NonNull
    final SubscriptionImpl<Fact> subscription;

    @NonNull
    final AtomicLong serial;

    @SuppressWarnings("FieldCanBeLocal")
    private long clientId = 0;

    public LinkedList<Fact> doFetch(PGCatchUpFetchPage fetch) {
        if (idsOnly()) {
            return fetch.fetchIdFacts(serial);
        } else {
            return fetch.fetchFacts(serial);
        }
    }

    private boolean idsOnly() {
        return request.idOnly() && postQueryMatcher.canBeSkipped();
    }

    @Override
    public void run() {
        PGCatchUpPrepare prep = new PGCatchUpPrepare(jdbc, request);
        clientId = prep.prepareCatchup(serial);
        if (clientId > 0) {
            try {
                final boolean idsOnly = idsOnly();
                final int queueSize = idsOnly ? props.getQueueSizeForIds() : props.getQueueSize();
                final int fetchSize = idsOnly ? props.getFetchSizeForIds() : props.getFetchSize();
                PGCatchupQueue q = new PGCatchupQueue(queueSize);
                PGCatchUpFetchPage fetch = new PGCatchUpFetchPage(jdbc, fetchSize, request,
                        clientId);
                Runnable refill = () -> {
                    while (true) {
                        LinkedList<Fact> facts = doFetch(fetch);
                        if (facts.isEmpty()) {
                            // we have reached the end
                            q.notifyDone();
                            break;
                        }
                        facts.stream().filter(postQueryMatcher).forEachOrdered(f -> {
                            try {
                                if (!q.offer(f, 15, TimeUnit.MINUTES)) {
                                    log.warn(
                                            "Did not suceed inserting into queue for {} for 15min. Assuming dead. Closing Subscription.",
                                            subscription);
                                    try {
                                        subscription.close();
                                    } catch (Exception e) {
                                        log.warn("While closing Subscription", e);
                                    }
                                    throw new SubscriptionCancelledException(
                                            "Queue insertion timeout. Subscription closed.");
                                }
                            } catch (InterruptedException ignored) {
                            }
                        });
                    }
                };
                CompletableFuture.runAsync(refill);
                while (true) {
                    Fact f = q.poll(50, TimeUnit.MILLISECONDS);
                    if (f != null) {
                        UUID factId = f.id();
                        try {
                            subscription.notifyElement(f);
                            log.trace("{} notifyElement called with id={}", request, factId);
                        } catch (Throwable e) {
                            // debug level, because it happens regularly
                            // on
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
                        if (q.isDone()) {
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                log.error("While fetching ", e);
            } finally {
                jdbc.update(PGConstants.DELETE_CATCH_BY_CID, clientId);
            }
        }
    }
}
