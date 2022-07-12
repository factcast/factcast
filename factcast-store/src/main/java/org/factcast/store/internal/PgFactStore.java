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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.atomic.*;

import org.factcast.core.DuplicateFactException;
import org.factcast.core.Fact;
import org.factcast.core.snap.Snapshot;
import org.factcast.core.snap.SnapshotId;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.store.AbstractFactStore;
import org.factcast.core.store.State;
import org.factcast.core.store.StateToken;
import org.factcast.core.store.TokenStore;
import org.factcast.core.subscription.FactTransformerService;
import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.core.subscription.TransformationException;
import org.factcast.core.subscription.observer.FactObserver;
import org.factcast.store.internal.lock.FactTableWriteLock;
import org.factcast.store.internal.query.PgFactIdToSerialMapper;
import org.factcast.store.internal.query.PgQueryBuilder;
import org.factcast.store.internal.snapcache.PgSnapshotCache;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.google.common.collect.Lists;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * A PostgreSQL based FactStore implementation
 *
 * @author uwe.schaefer@prisma-capacity.eu
 */
@Slf4j
public class PgFactStore extends AbstractFactStore {

  @NonNull private final JdbcTemplate jdbcTemplate;

  @NonNull private final PgSubscriptionFactory subscriptionFactory;

  @NonNull private final FactTableWriteLock lock;

  @NonNull private final FactTransformerService factTransformerService;
  @NonNull private final PgFactIdToSerialMapper pgFactIdToSerialMapper;

  @NonNull private final PgMetrics metrics;

  @NonNull private final PgSnapshotCache snapCache;

  @Autowired
  public PgFactStore(
      @NonNull JdbcTemplate jdbcTemplate,
      @NonNull PgSubscriptionFactory subscriptionFactory,
      @NonNull TokenStore tokenStore,
      @NonNull FactTableWriteLock lock,
      @NonNull FactTransformerService factTransformerService,
      @NonNull PgFactIdToSerialMapper pgFactIdToSerialMapper,
      @NonNull PgSnapshotCache snapCache,
      @NonNull PgMetrics metrics) {
    super(tokenStore);

    this.jdbcTemplate = jdbcTemplate;
    this.subscriptionFactory = subscriptionFactory;
    this.lock = lock;
    this.pgFactIdToSerialMapper = pgFactIdToSerialMapper;
    this.snapCache = snapCache;
    this.metrics = metrics;
    this.factTransformerService = factTransformerService;
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

    var fact = fetchById(id);
    return fact.map(
        value ->
            factTransformerService.transformIfNecessary(value, Collections.singleton(version)));
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRED)
  public void publish(@NonNull List<? extends Fact> factsToPublish) {
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
    return metrics.time(
        StoreMetrics.OP.ENUMERATE_NAMESPACES,
        () ->
            new HashSet<>(
                jdbcTemplate.query(
                    PgConstants.SELECT_DISTINCT_NAMESPACE, this::extractStringFromResultSet)));
  }

  @Override
  public @NonNull Set<String> enumerateTypes(@NonNull String ns) {
    return metrics.time(
        StoreMetrics.OP.ENUMERATE_TYPES,
        () ->
            new HashSet<>(
                jdbcTemplate.query(
                    PgConstants.SELECT_DISTINCT_TYPE_IN_NAMESPACE,
                    new Object[] {ns},
                    this::extractStringFromResultSet)));
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRED)
  public boolean publishIfUnchanged(
      @NonNull List<? extends Fact> factsToPublish, @NonNull Optional<StateToken> optionalToken) {
    return metrics.time(
        StoreMetrics.OP.PUBLISH_IF_UNCHANGED,
        () -> {
          lock.aquireExclusiveTXLock();
          return super.publishIfUnchanged(factsToPublish, optionalToken);
        });
  }

  @Override
  protected State getStateFor(@NonNull List<FactSpec> specs) {
    return doGetState(specs, 0);
  }

  @Override
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

          try {
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
          } catch (EmptyResultDataAccessException lastSerialIs0Then) {
            return State.of(specs, 0);
          }
        });
  }

  @Override
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
  public @NonNull Optional<Snapshot> getSnapshot(@NonNull SnapshotId id) {
    return metrics.time(StoreMetrics.OP.GET_SNAPSHOT, () -> snapCache.getSnapshot(id));
  }

  @Override
  public void setSnapshot(@NonNull Snapshot snapshot) {
    metrics.time(StoreMetrics.OP.SET_SNAPSHOT, () -> snapCache.setSnapshot(snapshot));
  }

  @Override
  public void clearSnapshot(@NonNull SnapshotId id) {
    metrics.time(StoreMetrics.OP.CLEAR_SNAPSHOT, () -> snapCache.clearSnapshot(id));
  }
}
