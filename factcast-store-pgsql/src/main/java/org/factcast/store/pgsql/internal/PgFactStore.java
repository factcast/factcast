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
package org.factcast.store.pgsql.internal;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;

import org.factcast.core.Fact;
import org.factcast.core.store.AbstractFactStore;
import org.factcast.core.store.StateToken;
import org.factcast.core.store.TokenStore;
import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.core.subscription.observer.FactObserver;
import org.factcast.store.pgsql.internal.lock.FactTableWriteLock;
import org.factcast.store.pgsql.internal.metrics.PgMetricNames;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.annotation.Propagation;
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
public class PgFactStore extends AbstractFactStore {

    // is that interesting to configure?
    private static final int BATCH_SIZE = 500;

    @NonNull
    final JdbcTemplate jdbcTemplate;

    @NonNull
    final PgSubscriptionFactory subscriptionFactory;

    @NonNull
    final MetricRegistry registry;

    @NonNull
    final Counter publishFailedCounter;

    final Timer publishLatency;

    final PgMetricNames names = new PgMetricNames();

    private final Meter publishMeter;

    private final Timer fetchLatency;

    private final Timer seqLookupLatency;

    private final Timer namespaceLatency;

    private final Timer typeLatency;

    private final Meter subscriptionCatchupMeter;

    private final Meter subscriptionFollowMeter;

    private FactTableWriteLock lock;

    @Autowired
    public PgFactStore(JdbcTemplate jdbcTemplate, PgSubscriptionFactory subscriptionFactory,
            MetricRegistry registry, TokenStore tokenStore, FactTableWriteLock lock) {
        super(tokenStore);

        this.jdbcTemplate = jdbcTemplate;
        this.subscriptionFactory = subscriptionFactory;
        this.registry = registry;
        this.lock = lock;
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
    @Transactional(propagation = Propagation.REQUIRED)
    public void publish(@NonNull List<? extends Fact> factsToPublish) {

        try (Context time = publishLatency.time()) {
            lock.aquireExclusiveLock();

            List<Fact> copiedListOfFacts = Lists.newArrayList(factsToPublish);
            final int numberOfFactsToPublish = factsToPublish.size();
            log.trace("Inserting {} fact(s) in batches of {}", numberOfFactsToPublish, BATCH_SIZE);
            jdbcTemplate.batchUpdate(PgConstants.INSERT_FACT, copiedListOfFacts, BATCH_SIZE, (
                    statement, fact) -> {
                statement.setString(1, fact.jsonHeader());
                statement.setString(2, fact.jsonPayload());
            });
            // add serials to headers
            jdbcTemplate.batchUpdate(PgConstants.UPDATE_FACT_SERIALS, copiedListOfFacts, BATCH_SIZE,
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
        } finally {
            lock.release();
        }
    }

    private Fact extractFactFromResultSet(ResultSet resultSet, int rowNum) {
        return PgFact.from(resultSet);
    }

    @NonNull
    private String extractStringFromResultSet(ResultSet resultSet, int rowNum) throws SQLException {
        return resultSet.getString(1);
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
            return jdbcTemplate.query(PgConstants.SELECT_BY_ID, new Object[] { "{\"id\":\"" + id
                    + "\"}" },
                    this::extractFactFromResultSet).stream().findFirst();
        }
    }

    @Override
    public OptionalLong serialOf(UUID l) {
        try (Context time = seqLookupLatency.time()) {
            Long res = jdbcTemplate.queryForObject(PgConstants.SELECT_SER_BY_ID,
                    new Object[] { "{\"id\":\"" + l + "\"}" }, Long.class);

            if (res != null && res.longValue() > 0) {
                return OptionalLong.of(res.longValue());
            }

        } catch (EmptyResultDataAccessException ignore) {
        }
        return OptionalLong.empty();
    }

    @Override
    public Set<String> enumerateNamespaces() {
        try (Context time = namespaceLatency.time()) {
            return new HashSet<>(
                    jdbcTemplate.query(PgConstants.SELECT_DISTINCT_NAMESPACE,
                            this::extractStringFromResultSet));
        }
    }

    @Override
    public Set<String> enumerateTypes(String ns) {
        try (Context time = typeLatency.time()) {
            return new HashSet<>(jdbcTemplate.query(PgConstants.SELECT_DISTINCT_TYPE_IN_NAMESPACE,
                    new Object[] { ns },
                    this::extractStringFromResultSet));
        }
    }

    @Override
    protected Map<UUID, Optional<UUID>> getStateFor(String ns, Collection<UUID> forAggIds) {
        // just prototype code
        // can probably be optimized, suggestions/PRs welcome
        RowMapper<Optional<UUID>> rse = (rs, i) -> Optional.of(UUID.fromString(rs
                .getString(1)));
        Map<UUID, Optional<UUID>> ret = new LinkedHashMap<UUID, Optional<UUID>>();
        for (UUID uuid : forAggIds) {

            String json = "{\"ns\":\"" + ns + "\",\"aggIds\":[\"" + uuid + "\"]}";

            try {
                ret.put(uuid, jdbcTemplate.queryForObject(
                        PgConstants.SELECT_LATEST_FACTID_FOR_AGGID,
                        new Object[] {
                                json }, rse));
            } catch (EmptyResultDataAccessException dont_care) {
                ret.put(uuid, Optional.empty());
            }
        }

        return ret;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public boolean publishIfUnchanged(@NonNull StateToken token,
            @NonNull List<? extends Fact> factsToPublish) {
        try {
            lock.aquireExclusiveLock();
            return super.publishIfUnchanged(token, factsToPublish);
        } finally {
            lock.release();
        }
    }
}
