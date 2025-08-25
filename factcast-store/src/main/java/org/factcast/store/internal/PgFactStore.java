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

import java.sql.*;
import java.sql.Date;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nullable;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.*;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.store.*;
import org.factcast.core.subscription.*;
import org.factcast.core.subscription.observer.FactObserver;
import org.factcast.core.subscription.transformation.*;
import org.factcast.store.StoreConfigurationProperties;
import org.factcast.store.internal.concurrency.*;
import org.factcast.store.internal.query.*;
import org.factcast.store.registry.SchemaRegistry;
import org.jetbrains.annotations.NotNull;
import org.springframework.dao.*;
import org.springframework.jdbc.core.*;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A PostgreSQL based FactStore implementation
 *
 * @author uwe.schaefer@prisma-capacity.eu
 */
@Slf4j
@SuppressWarnings({"java:S6809", "SpringTransactionalMethodCallsInspection"})
public class PgFactStore extends AbstractFactStore {

  @NonNull private final JdbcTemplate jdbcTemplate;
  @NonNull private final SchemaRegistry schemaRegistry;

  @NonNull private final PgSubscriptionFactory subscriptionFactory;

  @NonNull private final ConcurrencyStrategy concurrencyStrategy;
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
      @NonNull ConcurrencyStrategy concurrencyStrategy,
      @NonNull FactTransformerService factTransformerService,
      @NonNull PgFactIdToSerialMapper pgFactIdToSerialMapper,
      @NonNull PgMetrics metrics,
      @NonNull StoreConfigurationProperties props,
      @NonNull PlatformTransactionManager platformTransactionManager) {
    super(tokenStore);

    this.jdbcTemplate = jdbcTemplate;
    this.subscriptionFactory = subscriptionFactory;
    this.schemaRegistry = schemaRegistry;
    this.concurrencyStrategy = concurrencyStrategy;
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
  public void publish(@NonNull List<? extends Fact> factsToPublish) {
    if (props.isReadOnlyModeEnabled()) {
      throw new UnsupportedOperationException("Publishing is not allowed in read-only mode");
    }

    metrics.time(
        StoreMetrics.OP.PUBLISH,
        () -> {
          try {
            concurrencyStrategy.publish(factsToPublish);
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

  @NonNull
  private Integer extractIntFromResultSet(
      ResultSet resultSet, @SuppressWarnings("unused") int rowNum) throws SQLException {
    return resultSet.getInt(1);
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
    if (schemaRegistry.isActive() && !props.isEnumerationDirectModeEnabled()) {
      return schemaRegistry.enumerateNamespaces();
    } else {
      return enumerateNamespacesFromPg();
    }
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
    if (schemaRegistry.isActive() && !props.isEnumerationDirectModeEnabled()) {
      return schemaRegistry.enumerateTypes(ns);
    } else {
      return enumerateTypesFromPg(ns);
    }
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
  public @NonNull Set<Integer> enumerateVersions(@NonNull String ns, @NonNull String type) {
    if (schemaRegistry.isActive() && !props.isEnumerationDirectModeEnabled()) {
      return schemaRegistry.enumerateVersions(ns, type);
    } else {
      return enumerateVersionsFromPg(ns, type);
    }
  }

  public @NonNull Set<Integer> enumerateVersionsFromPg(@NonNull String ns, @NonNull String type) {
    return metrics.time(
        StoreMetrics.OP.ENUMERATE_VERSIONS,
        () ->
            new HashSet<>(
                jdbcTemplate.query(
                    PgConstants.SELECT_DISTINCT_VERSIONS_FOR_NS_AND_TYPE,
                    this::extractIntFromResultSet,
                    ns,
                    type)));
  }

  @Override
  public boolean publishIfUnchanged(
      @NonNull List<? extends Fact> factsToPublish, @NonNull Optional<StateToken> optionalToken) {
    if (props.isReadOnlyModeEnabled()) {
      throw new UnsupportedOperationException("Publishing is not allowed in read-only mode");
    }

    return metrics.time(
        StoreMetrics.OP.PUBLISH_IF_UNCHANGED,
        () ->
            concurrencyStrategy.publishIfUnchanged(
                factsToPublish, until -> hasNoConflictingChangeUntil(until, optionalToken)));
  }

  @Override
  @NonNull
  protected State getStateFor(
      @NonNull List<FactSpec> specs, long lastMatchingSerial, @Nullable Long toExclusive) {
    return doGetState(specs, lastMatchingSerial, toExclusive);
  }

  private State doGetState(
      @NotNull List<FactSpec> specs, long lastMatchingSerial, @Nullable Long toExclusive) {
    return metrics.time(
        StoreMetrics.OP.GET_STATE_FOR_WITH_START,
        () -> {
          PgQueryBuilder pgQueryBuilder = new PgQueryBuilder(specs);
          String stateSQL = pgQueryBuilder.createStateSQL(toExclusive);
          PreparedStatementSetter statementSetter =
              pgQueryBuilder.createStatementSetter(new AtomicLong(lastMatchingSerial), toExclusive);

          ResultSetExtractor<Long> rch =
              resultSet -> {
                if (!resultSet.next()) {
                  return 0L;
                } else {
                  return resultSet.getLong(1);
                }
              };
          long lastSerial = jdbcTemplate.query(stateSQL, statementSetter, rch);
          return State.of(specs, lastSerial);
        });
  }

  // TODO really necessary? most of the time we could hint at a min ser
  // this way it always needs to run from 0
  // remove if at all possible, maybe defaulting to 0 when we cannot get any info from the client
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

  @Override
  public Long firstSerialAfter(@NonNull LocalDate date) {
    try {
      return jdbcTemplate.queryForObject(
          PgConstants.FIRST_SERIAL_AFTER_DATE,
          new SingleColumnRowMapper<>(Long.class),
          Date.valueOf(date));
    } catch (EmptyResultDataAccessException noFactsAtAll) {
      return null;
    }
  }
}
