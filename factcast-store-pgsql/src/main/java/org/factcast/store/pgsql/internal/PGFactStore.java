package org.factcast.store.pgsql.internal;

import java.sql.BatchUpdateException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.factcast.core.Fact;
import org.factcast.core.store.FactStore;
import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.core.subscription.observer.FactObserver;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.Lists;
import com.impossibl.postgres.jdbc.PGSQLIntegrityConstraintViolationException;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * A PostgreSQL based FactStore implementation
 * 
 * @author uwe.schaefer@mercateo.com
 *
 */
@Slf4j

class PGFactStore implements FactStore {
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

    final Meter publishLatency;

    final PGMetricNames names = new PGMetricNames();

    PGFactStore(JdbcTemplate jdbcTemplate, PGSubscriptionFactory subscriptionFactory,
            MetricRegistry registry) {
        this.jdbcTemplate = jdbcTemplate;
        this.subscriptionFactory = subscriptionFactory;
        this.registry = registry;

        publishFailedCounter = registry.counter(names.factPublishingFailed());
        publishLatency = registry.meter(names.factPublishingLatency());
    }

    @Override
    @Transactional
    public void publish(@NonNull List<? extends Fact> factsToPublish) {
        try {

            List<Fact> copiedListOfFacts = Lists.newArrayList(factsToPublish);
            final int numberOfFactsToPublish = factsToPublish.size();

            log.trace("Inserting {} fact(s) in batches of {}", numberOfFactsToPublish, BATCH_SIZE);

            jdbcTemplate.batchUpdate(PGConstants.INSERT_FACT, copiedListOfFacts, BATCH_SIZE, (
                    statement, fact) -> {
                statement.setString(1, fact.jsonHeader());
                statement.setString(2, fact.jsonPayload());
            });

            publishLatency.mark(numberOfFactsToPublish);

        } catch (UncategorizedSQLException sql) {

            publishFailedCounter.inc();

            // yikes
            Throwable batch = sql.getCause();
            if (batch instanceof BatchUpdateException) {
                Throwable violation = batch.getCause();
                if (violation instanceof PGSQLIntegrityConstraintViolationException) {
                    throw new IllegalArgumentException(violation);
                }
            }
            throw sql;
        }
    }

    private Fact extractFactFromResultSet(ResultSet resultSet, int rowNum) throws SQLException {
        return PGFact.from(resultSet);
    }

    @Override
    public Subscription subscribe(@NonNull SubscriptionRequestTO request,
            @NonNull FactObserver observer) {
        return subscriptionFactory.subscribe(request, observer);
    }

    @Override
    public Optional<Fact> fetchById(@NonNull UUID id) {
        return jdbcTemplate.query(PGConstants.SELECT_BY_ID, new Object[] { "{\"id\":\"" + id
                + "\"}" }, this::extractFactFromResultSet).stream().findFirst();
    }

}
