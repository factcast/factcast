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

import com.google.common.annotations.VisibleForTesting;
import java.sql.*;
import java.sql.Date;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.*;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.store.*;
import org.factcast.core.subscription.*;
import org.factcast.core.subscription.observer.*;
import org.factcast.core.util.ExceptionHelper;
import org.factcast.store.StoreConfigurationProperties;
import org.factcast.store.internal.horizon.FactStreamHorizonProvider;
import org.factcast.store.internal.lock.FactTableWriteLock;
import org.factcast.store.internal.query.*;
import org.factcast.store.internal.transformation.*;
import org.factcast.store.registry.SchemaRegistry;
import org.jetbrains.annotations.NotNull;
import org.springframework.dao.*;
import org.springframework.jdbc.core.*;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
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

  @NonNull private final FactStreamHorizonProvider horizonProvider;

  @NonNull private final StoreConfigurationProperties props;

  private final @NonNull PublicationQueue queue;
  private final @NonNull TransactionTemplate tx;

  public PgFactStore(
      @NonNull JdbcTemplate jdbcTemplate,
      @NonNull PgSubscriptionFactory subscriptionFactory,
      @NonNull TokenStore tokenStore,
      @NonNull SchemaRegistry schemaRegistry,
      @NonNull FactTableWriteLock lock,
      @NonNull FactTransformerService factTransformerService,
      @NonNull PgFactIdToSerialMapper pgFactIdToSerialMapper,
      @NonNull PgMetrics metrics,
      @NonNull FactStreamHorizonProvider horizonProvider,
      @NonNull StoreConfigurationProperties props,
      @NonNull PlatformTransactionManager platformTransactionManager) {
    super(tokenStore);

    this.jdbcTemplate = jdbcTemplate;
    this.subscriptionFactory = subscriptionFactory;
    this.schemaRegistry = schemaRegistry;
    this.lock = lock;
    this.pgFactIdToSerialMapper = pgFactIdToSerialMapper;
    this.metrics = metrics;
    this.horizonProvider = horizonProvider;
    this.factTransformerService = factTransformerService;
    this.props = props;

    this.tx = new TransactionTemplate(platformTransactionManager);
    this.queue =
        new PublicationQueue(
            this,
            metrics,
            props.getPublishBatched().getMaxBatchSize(),
            props.getPublishBatched().getFlushDelayMillis());
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
                    this::extractFactFromResultSet,
                    "{\"id\":\"" + id + "\"}")
                .stream()
                .findFirst());
  }

  @Override
  public @NonNull Optional<Fact> fetchByIdAndVersion(@NonNull UUID id, int version)
      throws TransformationException {
    return fetchById(id)
        .map(PgFact.class::cast)
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
    var defensiveCopy = new ArrayList<>(factsToPublish);

    // Keep publication in the caller's transaction so its locks and rollback apply.
    if (props.getPublishBatched().isEnabled()
        && !TransactionSynchronizationManager.isActualTransactionActive()) {
      publish_usingBatching(defensiveCopy);
    } else {
      publish_withoutBatching(defensiveCopy);
    }
  }

  @VisibleForTesting
  Fact extractFactFromResultSet(ResultSet resultSet, @SuppressWarnings("unused") int rowNum)
      throws SQLException {
    return PgFact.from(resultSet);
  }

  @NonNull
  @VisibleForTesting
  String extractStringFromResultSet(ResultSet resultSet, @SuppressWarnings("unused") int rowNum)
      throws SQLException {
    return resultSet.getString(1);
  }

  @NonNull
  @VisibleForTesting
  Integer extractIntFromResultSet(ResultSet resultSet, @SuppressWarnings("unused") int rowNum)
      throws SQLException {
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
        tx.execute(
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
  @Transactional(propagation = Propagation.REQUIRED)
  @SuppressWarnings("java:S6809") // as we started a transaction already
  public boolean publishIfUnchanged(
      @NonNull List<? extends Fact> factsToPublish, @NonNull Optional<StateToken> optionalToken) {
    if (optionalToken.isEmpty()) {
      // even though this fallback behavior already is present in super, we branch here to avoid
      // double (and unnecessarily exclusive) locking
      publish(factsToPublish);
      return true;
    }
    if (props.isReadOnlyModeEnabled()) {
      throw new UnsupportedOperationException("Publishing is not allowed in read-only mode");
    }

    var defensiveCopy = new ArrayList<>(factsToPublish);

    return metrics.time(
        StoreMetrics.OP.PUBLISH_IF_UNCHANGED,
        () -> {
          // The exclusive lock waits for shared-lock holders and protects validation through
          // commit. Queued publications may follow because their publish calls have not returned.
          lock.acquireExclusiveTXLock();
          boolean successful = PgFactStore.super.publishIfUnchanged(defensiveCopy, optionalToken);
          if (!successful)
            metrics.counter(StoreMetrics.EVENT.UNSUCCESSFUL_CONDITIONAL_PUBLISH).increment();
          return successful;
        });
  }

  @Override
  @NonNull
  protected State getStateFor(@NonNull Collection<FactSpec> specs) {
    return getCurrentStateFor(specs);
  }

  @Override
  @NonNull
  protected State getStateFor(@NonNull Collection<FactSpec> specs, long lastMatchingSerial) {
    return doGetState(specs, lastMatchingSerial);
  }

  @VisibleForTesting
  State doGetState(@NotNull Collection<FactSpec> specs, long lastMatchingSerial) {
    return metrics.time(
        StoreMetrics.OP.GET_STATE_FOR,
        () -> {
          PgQueryBuilder pgQueryBuilder =
              new PgQueryBuilder(specs, props.isInternalExclusionEnabled());
          int backwardScanWindow = props.getStateQueryBackwardScanWindow();
          String stateSQL = pgQueryBuilder.createStateSQL(backwardScanWindow);
          PreparedStatementSetter statementSetter =
              pgQueryBuilder.createStateStatementSetter(lastMatchingSerial);

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

  @Override
  @NonNull
  protected State getCurrentStateFor(Collection<FactSpec> specs) {
    return metrics.time(
        StoreMetrics.OP.GET_STATE_FOR,
        () -> {
          FactStreamHorizon horizon = horizonProvider.get();
          return State.of(specs, horizon.factSerial());
        });
  }

  @SuppressWarnings("DataFlowIssue")
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
    // Committed rows can still be ahead of the safe stream boundary.
    return horizonProvider.get().factSerial();
  }

  @Override
  public long lastSerialBefore(@NonNull LocalDate date) {
    try {
      // ^ as we know this will return a long and cannot be empty
      long lastSer =
          jdbcTemplate.queryForObject(
              PgConstants.LAST_SERIAL_BEFORE_DATE,
              new SingleColumnRowMapper<>(Long.class),
              Date.valueOf(date));
      if (lastSer == -1) {
        // No date boundary exists yet, so return the latest safe serial.
        return latestSerial();
      } else {
        return Math.min(lastSer, horizonProvider.get().factSerial());
      }
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

  // Publication
  void publish_usingBatching(@NonNull List<? extends Fact> factsToPublish) {
    if (props.isReadOnlyModeEnabled()) {
      throw new UnsupportedOperationException("Publishing is not allowed in read-only mode");
    }
    metrics.time(
        StoreMetrics.OP.PUBLISH,
        () -> {
          try {
            queue.addAndFlush(factsToPublish).get();
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
          } catch (ExecutionException e) {
            throw ExceptionHelper.toRuntime(e.getCause());
          }
        });
  }

  void publish_withoutBatching(@NonNull List<? extends Fact> factsToPublish) {
    metrics.time(StoreMetrics.OP.PUBLISH, () -> doPublish(factsToPublish));
  }

  protected void doPublish(List<? extends Fact> facts) {
    int numberOfFactsToPublish = facts.size();
    log.trace("Inserting {} fact(s)", numberOfFactsToPublish);
    long transactionStarted = System.nanoTime();
    Exception transactionFailure = null;
    try {
      tx.execute(
          ts -> {
            try {
              long lockStarted = System.nanoTime();
              Exception lockFailure = null;
              try {
                lock.acquireExclusiveTXLock();
              } catch (RuntimeException e) {
                lockFailure = e;
                throw e;
              } finally {
                metrics.recordDuration(
                    StoreMetrics.OP.PUBLICATION_LOCK, System.nanoTime() - lockStarted, lockFailure);
              }
              jdbcTemplate.batchUpdate(
                  PgConstants.INSERT_FACT,
                  facts,
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

            return null;
          });
    } catch (TransactionException e) {
      transactionFailure = e;
      throw ExceptionHelper.toRuntime(e.getCause());
    } catch (RuntimeException e) {
      transactionFailure = e;
      throw e;
    } finally {
      metrics.recordDuration(
          StoreMetrics.OP.PUBLICATION_TRANSACTION,
          System.nanoTime() - transactionStarted,
          transactionFailure);
    }
  }
}
