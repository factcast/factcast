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
package org.factcast.store.pgsql.internal;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;

import org.factcast.core.Fact;
import org.factcast.core.store.FactStore;
import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.core.subscription.observer.FactObserver;
import org.factcast.store.pgsql.internal.metrics.PGMetricNames;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.codahale.metrics.Timer.Context;
import com.google.common.collect.Lists;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * A PostgreSQL based FactStore implementation
 *
 * @author uwe.schaefer@mercateo.com
 */
@Slf4j
@Component("factStore")
public class PGFactStore implements FactStore {

    // is that interesting to configure?
    private static final int BATCH_SIZE = 500;

    @NonNull
    final JdbcTemplate jdbcTemplate;

    @NonNull
    final PGSubscriptionFactory subscriptionFactory;

    @NonNull
    final MetricRegistry registry;

    @NonNull
    final Counter publishFailedCounter;

    final Timer publishLatency;

    final PGMetricNames names = new PGMetricNames();

    private final Meter publishMeter;

    private final Timer fetchLatency;

    private final Timer seqLookupLatency;

    private final Timer namespaceLatency;

    private final Timer typeLatency;

    private final Meter subscriptionCatchupMeter;

    private final Meter subscriptionFollowMeter;

    @Autowired
    public PGFactStore(JdbcTemplate jdbcTemplate, PGSubscriptionFactory subscriptionFactory,
            MetricRegistry registry) {
        this.jdbcTemplate = jdbcTemplate;
        this.subscriptionFactory = subscriptionFactory;
        this.registry = registry;
        publishFailedCounter = registry.counter(names.factPublishingFailed());
        publishLatency = registry.timer(names.factPublishingLatency());
        publishMeter = registry.meter(names.factPublishingMeter());
        fetchLatency = registry.timer(names.fetchLatency());
        namespaceLatency = registry.timer(names.namespaceLatency());
        typeLatency = registry.timer(names.typeLatency());
        seqLookupLatency = registry.timer(names.seqLookupLatency());
        subscriptionCatchupMeter = registry.meter(names.subscribeCatchup());
        subscriptionFollowMeter = registry.meter(names.subscribeFollow());
    }

    @Override
    @Transactional
    public void publish(@NonNull List<? extends Fact> factsToPublish) {
        try (Context time = publishLatency.time()) {
            List<Fact> copiedListOfFacts = Lists.newArrayList(factsToPublish);
            final int numberOfFactsToPublish = factsToPublish.size();
            log.trace("Inserting {} fact(s) in batches of {}", numberOfFactsToPublish, BATCH_SIZE);
            jdbcTemplate.batchUpdate(PGConstants.INSERT_FACT, copiedListOfFacts, BATCH_SIZE, (
                    statement, fact) -> {
                statement.setString(1, fact.jsonHeader());
                statement.setString(2, fact.jsonPayload());
            });
            // add serials to headers
            jdbcTemplate.batchUpdate(PGConstants.UPDATE_FACT_SERIALS, copiedListOfFacts, BATCH_SIZE,
                    (statement, fact) -> {
                        final String idMatch = "{\"id\":\"" + fact.id() + "\"}";
                        statement.setString(1, idMatch);
                    });
            publishMeter.mark(numberOfFactsToPublish);
        } catch (DuplicateKeyException dupkey) {
            publishFailedCounter.inc();
            throw new IllegalArgumentException(dupkey.getMessage());
        } catch (DataAccessException sql) {
            publishFailedCounter.inc();
            throw sql;
        }
    }

    private Fact extractFactFromResultSet(ResultSet resultSet, int rowNum) {
        return PGFact.from(resultSet);
    }

    @NonNull
    private String extractStringFromResultSet(@NonNull ResultSet resultSet, int rowNum)
            throws SQLException {
        return resultSet.getString(1);
    }

    @NonNull
    private Long extractSerFromResultSet(@NonNull ResultSet resultSet, int rowNum)
            throws SQLException {
        return Long.valueOf(resultSet.getString(PGConstants.COLUMN_SER));
    }

    @Override
    public Subscription subscribe(@NonNull SubscriptionRequestTO request,
            @NonNull FactObserver observer) {
        if (request.continuous()) {
            subscriptionFollowMeter.mark();
        } else {
            subscriptionCatchupMeter.mark();
        }
        return subscriptionFactory.subscribe(request, observer);
    }

    @Override
    public Optional<Fact> fetchById(@NonNull UUID id) {
        try (Context time = fetchLatency.time()) {
            return jdbcTemplate.query(PGConstants.SELECT_BY_ID, new Object[] { "{\"id\":\"" + id
                    + "\"}" }, this::extractFactFromResultSet).stream().findFirst();
        }
    }

    @Override
    public OptionalLong serialOf(UUID l) {
        try (Context time = seqLookupLatency.time()) {
            List<Long> res = jdbcTemplate.query(PGConstants.SELECT_SER_BY_ID, new Object[] {
                    "{\"id\":\"" + l + "\"}" }, this::extractSerFromResultSet);
            if (res.size() > 1) {
                throw new IllegalStateException("Event ID appeared twice!?");
            } else if (res.isEmpty()) {
                return OptionalLong.empty();
            }
            Long ser = res.get(0);
            if (ser != null && ser > 0) {
                return OptionalLong.of(ser);
            } else {
                return OptionalLong.empty();
            }
        }
    }

    @Override
    public Set<String> enumerateNamespaces() {
        try (Context time = namespaceLatency.time()) {
            return new HashSet<>(jdbcTemplate.query(PGConstants.SELECT_DISTINCT_NAMESPACE,
                    this::extractStringFromResultSet));
        }
    }

    @Override
    public Set<String> enumerateTypes(String ns) {
        try (Context time = typeLatency.time()) {
            return new HashSet<>(jdbcTemplate.query(PGConstants.SELECT_DISTINCT_TYPE_IN_NAMESPACE,
                    new Object[] { ns }, this::extractStringFromResultSet));
        }
    }
}
