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
package org.factcast.store.internal;

import com.google.common.collect.Lists;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.DuplicateFactException;
import org.factcast.core.Fact;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.store.AbstractFactStore;
import org.factcast.core.store.State;
import org.factcast.core.store.StateToken;
import org.factcast.core.store.TokenStore;
import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.core.subscription.TransformationException;
import org.factcast.core.subscription.observer.FactObserver;
import org.factcast.core.subscription.transformation.FactTransformerService;
import org.factcast.core.subscription.transformation.TransformationRequest;
import org.factcast.store.StoreConfigurationProperties;
import org.factcast.store.internal.lock.FactTableWriteLock;
import org.factcast.store.internal.query.PgFactIdToSerialMapper;
import org.factcast.store.internal.query.PgQueryBuilder;
import org.factcast.store.registry.SchemaRegistry;
import org.jetbrains.annotations.NotNull;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.SingleColumnRowMapper;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A PostgreSQL based FactStore implementation
 *
 * @author uwe.schaefer@prisma-capacity.eu
 */
@Slf4j
public class PgFactStore extends AbstractFactStore {

  @NonNull private final JdbcTemplate jdbcTemplate;
  @NonNull private final SchemaRegistry schemaRegistry;

  @NonNull private final PgSubscriptionFactory subscriptionFactory;

  @NonNull private final FactTableWriteLock lock;

  @NonNull private final FactTransformerService factTransformerService;
  @NonNull private final PgFactIdToSerialMapper pgFactIdToSerialMapper;

  @NonNull private final PgMetrics metrics;

  @NonNull private final StoreConfigurationProperties props;

  @NonNull private final PlatformTransactionManager platformTransactionManager;

  public PgFactStore(
      @NonNull JdbcTemplate jdbcTemplate,
      @NonNull PgSubscriptionFactory subscriptionFactory,
      @NonNull TokenStore tokenStore,
      @NonNull SchemaRegistry schemaRegistry,
      @NonNull FactTableWriteLock lock,
      @NonNull FactTransformerService factTransformerService,
      @NonNull PgFactIdToSerialMapper pgFactIdToSerialMapper,
      @NonNull PgMetrics metrics,
      @NonNull StoreConfigurationProperties props,
      @NonNull PlatformTransactionManager platformTransactionManager) {
    super(tokenStore);

    this.jdbcTemplate = jdbcTemplate;
    this.subscriptionFactory = subscriptionFactory;
    this.schemaRegistry = schemaRegistry;
    this.lock = lock;
    this.pgFactIdToSerialMapper = pgFactIdToSerialMapper;
    this.metrics = metrics;
    this.factTransformerService = factTransformerService;
    this.props = props;
    this.platformTransactionManager = platformTransactionManager;
  }

  @Override
  public @NonNull Optional<Fact> fetchById(@NonNull UUID id) {
    // replace on merge with faster version
    return metrics.time(
        StoreMetrics.OP.FETCH_BY_ID,
        () ->
            jdbcTemplate
                .query(
                    PgConstants.SELECT_BY_ID,
                    new Object[] {"{\"id\":\"" + id + "\"}"},
                    this::extractFactFromResultSet)
                .stream()
                .findFirst());
  }

  @Override
  public @NonNull Optional<Fact> fetchByIdAndVersion(@NonNull UUID id, int version)
      throws TransformationException {
    return fetchById(id)
        .map(
            value ->
                factTransformerService.transform(
                    new TransformationRequest(value, Collections.singleton(version))));
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRED)
  public void publish(@NonNull List<? extends Fact> factsToPublish) {
    if (props.isReadOnlyModeEnabled()) {
      throw new UnsupportedOperationException("Publishing is not allowed in read-only mode");
    }

    metrics.time(
        StoreMetrics.OP.PUBLISH,
        () -> {
          try {
            lock.aquireExclusiveTXLock();

            List<Fact> copiedListOfFacts = Lists.newArrayList(factsToPublish);
            int numberOfFactsToPublish = factsToPublish.size();
            log.trace("Inserting {} fact(s)", numberOfFactsToPublish);
            jdbcTemplate.batchUpdate(
                PgConstants.INSERT_FACT,
                copiedListOfFacts,
                // batch limitation not necessary
                Integer.MAX_VALUE,
                (statement, fact) -> {
                  statement.setString(1, fact.jsonHeader());
                  statement.setString(2, fact.jsonPayload());
                });
            // adding serials to headers is done via trigger

          } catch (DuplicateKeyException dupkey) {
            throw new DuplicateFactException(dupkey.getMessage());
          }
        });
  }

  private Fact extractFactFromResultSet(ResultSet resultSet, @SuppressWarnings("unused") int rowNum)
      throws SQLException {
    return PgFact.from(resultSet);
  }

  @NonNull
  private String extractStringFromResultSet(
      ResultSet resultSet, @SuppressWarnings("unused") int rowNum) throws SQLException {
    return resultSet.getString(1);
  }

  @Override
  public @NonNull Subscription subscribe(
      @NonNull SubscriptionRequestTO request, @NonNull FactObserver observer) {
    StoreMetrics.OP operation =
        request.continuous() ? StoreMetrics.OP.SUBSCRIBE_FOLLOW : StoreMetrics.OP.SUBSCRIBE_CATCHUP;
    return metrics.time(operation, () -> subscriptionFactory.subscribe(request, observer));
  }

  @Override
  public @NonNull OptionalLong serialOf(@NonNull UUID factId) {

    long serial = pgFactIdToSerialMapper.retrieve(factId);
    if (serial == 0) {
      return OptionalLong.empty();
    } else {
      return OptionalLong.of(serial);
    }
  }

  @Override
  public @NonNull Set<String> enumerateNamespaces() {
    if (schemaRegistry.isActive() && !props.isEnumerationDirectModeEnabled())
      return schemaRegistry.enumerateNamespaces();
    else return enumerateNamespacesFromPg();
  }

  public @NonNull Set<String> enumerateNamespacesFromPg() {
    // wrap in TX to make SET LOCAL work properly (and auto revert on commit/rollback)
    final var result =
        new TransactionTemplate(platformTransactionManager)
            .execute(
                status ->
                    metrics.time(
                        StoreMetrics.OP.ENUMERATE_NAMESPACES,
                        () -> {
                          // used because pg seems to favor the seq scan for even 80k rows over the
                          // index
                          jdbcTemplate.execute(PgConstants.DISABLE_SEQSCAN);

                          return new HashSet<>(
                              jdbcTemplate.query(
                                  PgConstants.SELECT_DISTINCT_NAMESPACE,
                                  this::extractStringFromResultSet));
                        }));

    return Objects.requireNonNull(result);
  }

  @Override
  public @NonNull Set<String> enumerateTypes(@NonNull String ns) {
    if (schemaRegistry.isActive() && !props.isEnumerationDirectModeEnabled())
      return schemaRegistry.enumerateTypes(ns);
    else return enumerateTypesFromPg(ns);
  }

  public @NonNull Set<String> enumerateTypesFromPg(@NonNull String ns) {
    return metrics.time(
        StoreMetrics.OP.ENUMERATE_TYPES,
        () ->
            new HashSet<>(
                jdbcTemplate.query(
                    PgConstants.SELECT_DISTINCT_TYPE_IN_NAMESPACE,
                    this::extractStringFromResultSet,
                    ns)));
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRED)
  public boolean publishIfUnchanged(
      @NonNull List<? extends Fact> factsToPublish, @NonNull Optional<StateToken> optionalToken) {
    if (props.isReadOnlyModeEnabled()) {
      throw new UnsupportedOperationException("Publishing is not allowed in read-only mode");
    }

    return metrics.time(
        StoreMetrics.OP.PUBLISH_IF_UNCHANGED,
        () -> {
          lock.aquireExclusiveTXLock();
          return super.publishIfUnchanged(factsToPublish, optionalToken);
        });
  }

  @Override
  @NonNull
  protected State getStateFor(@NonNull List<FactSpec> specs) {
    return doGetState(specs, 0);
  }

  @Override
  @NonNull
  protected State getStateFor(@NonNull List<FactSpec> specs, long lastMatchingSerial) {
    return doGetState(specs, lastMatchingSerial);
  }

  private State doGetState(@NotNull List<FactSpec> specs, long lastMatchingSerial) {
    return metrics.time(
        StoreMetrics.OP.GET_STATE_FOR,
        () -> {
          PgQueryBuilder pgQueryBuilder = new PgQueryBuilder(specs);
          String stateSQL = pgQueryBuilder.createStateSQL();
          PreparedStatementSetter statementSetter =
              pgQueryBuilder.createStatementSetter(new AtomicLong(lastMatchingSerial));

          ResultSetExtractor<Long> rch =
              new ResultSetExtractor<>() {
                @Override
                public Long extractData(ResultSet resultSet)
                    throws SQLException, DataAccessException {
                  if (!resultSet.next()) {
                    return 0L;
                  } else {
                    return resultSet.getLong(1);
                  }
                }
              };
          long lastSerial = jdbcTemplate.query(stateSQL, statementSetter, rch);
          return State.of(specs, lastSerial);
        });
  }

  @Override
  @NonNull
  protected State getCurrentStateFor(List<FactSpec> specs) {
    return metrics.time(
        StoreMetrics.OP.GET_STATE_FOR,
        () -> {
          long max =
              Objects.requireNonNull(
                  jdbcTemplate.queryForObject(PgConstants.LAST_SERIAL_IN_LOG, Long.class));
          return State.of(specs, max);
        });
  }

  @Override
  public long currentTime() {
    return jdbcTemplate.queryForObject(PgConstants.CURRENT_TIME_MILLIS, Long.class);
  }

  @Override
  public @NonNull Optional<Fact> fetchBySerial(long serial) {
    return metrics.time(
        StoreMetrics.OP.FETCH_BY_SER,
        () -> {
          try {
            return Optional.ofNullable(
                jdbcTemplate.queryForObject(
                    PgConstants.SELECT_BY_SER, this::extractFactFromResultSet, serial));
          } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
          }
        });
  }

  @Override
  public long latestSerial() {
    try {
      Long l =
          jdbcTemplate.queryForObject(
              PgConstants.HIGHWATER_SERIAL, new SingleColumnRowMapper<>(Long.class));
      return Optional.ofNullable(l).orElse(0L);
    } catch (EmptyResultDataAccessException noFactsAtAll) {
      return 0L;
    }
  }

  @Override
  public long lastSerialBefore(@NonNull LocalDate date) {
    try {
      Long lastSer =
          jdbcTemplate.queryForObject(
              PgConstants.LAST_SERIAL_BEFORE_DATE,
              new SingleColumnRowMapper<>(Long.class),
              Date.valueOf(date));
      return Optional.ofNullable(lastSer).orElse(0L);
    } catch (EmptyResultDataAccessException noFactsAtAll) {
      return 0L;
    }
  }
}
