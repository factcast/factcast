/*
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
package org.factcast.store.pgsql.internal.catchup.paged;

import java.util.LinkedList;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.factcast.core.Fact;
import org.factcast.core.subscription.SubscriptionImpl;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.store.pgsql.PgConfigurationProperties;
import org.factcast.store.pgsql.internal.PgConstants;
import org.factcast.store.pgsql.internal.PgPostQueryMatcher;
import org.factcast.store.pgsql.internal.catchup.PgCatchUpFetchPage;
import org.factcast.store.pgsql.internal.catchup.PgCatchUpPrepare;
import org.factcast.store.pgsql.internal.catchup.PgCatchup;
import org.factcast.store.pgsql.internal.query.PgFactIdToSerialMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class PgPagedCatchup implements PgCatchup {

    @NonNull
    final JdbcTemplate jdbc;

    @NonNull
    final PgConfigurationProperties props;

    @NonNull
    final PgFactIdToSerialMapper serMapper;

    @NonNull
    final SubscriptionRequestTO request;

    @NonNull
    final PgPostQueryMatcher postQueryMatcher;

    @NonNull
    final SubscriptionImpl<Fact> subscription;

    @NonNull
    final AtomicLong serial;

    @SuppressWarnings("FieldCanBeLocal")
    private long clientId = 0;

    private LinkedList<Fact> doFetch(PgCatchUpFetchPage fetch) {
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
        PgCatchUpPrepare prep = new PgCatchUpPrepare(jdbc, request);
        clientId = prep.prepareCatchup(serial);
        if (clientId > 0) {
            try {
                PgCatchUpFetchPage fetch = new PgCatchUpFetchPage(jdbc, idsOnly() ? props
                        .getPageSizeForIds() : props.getPageSize(), request, clientId);
                while (true) {
                    LinkedList<Fact> facts = doFetch(fetch);
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
                                    log.warn("{} exception while closing subscription: {}", request,
                                            e1.getMessage());
                                }
                                throw e;
                            }
                        } else {
                            log.trace("{} filtered id={}", request, factId);
                        }
                    }
                }
            } catch (Exception e) {
                log.error("While fetching ", e);
            } finally {
                jdbc.update(PgConstants.DELETE_CATCH_BY_CID, clientId);
            }
        }
    }
}
