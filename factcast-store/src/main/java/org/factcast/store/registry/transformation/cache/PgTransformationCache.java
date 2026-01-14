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
package org.factcast.store.registry.transformation.cache;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.*;
import java.sql.*;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.Fact;
import org.factcast.store.StoreConfigurationProperties;
import org.factcast.store.internal.PgFact;
import org.factcast.store.registry.metrics.RegistryMetrics;
import org.factcast.store.registry.metrics.RegistryMetrics.*;
import org.jetbrains.annotations.NotNull;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
public class PgTransformationCache implements TransformationCache, AutoCloseable {
  private final JdbcTemplate jdbcTemplate;
  private final RegistryMetrics registryMetrics;
  private final StoreConfigurationProperties storeConfigurationProperties;

  @Getter(AccessLevel.PACKAGE)
  final ThreadPoolExecutor tpe =
      new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>());

  private static final CompletableFuture<Void> COMPLETED_FUTURE =
      CompletableFuture.completedFuture(null);

  @Getter(AccessLevel.PROTECTED)
  @VisibleForTesting
  // entry of null means read, entry of non-null means write
  private final CacheBuffer buffer;

  private final PlatformTransactionManager platformTransactionManager;

  static final int THRESHOLD_PERCENT = 80;

  public final int maxBufferSize;
  private final int bufferThreshold;

  public PgTransformationCache(
      PlatformTransactionManager platformTransactionManager,
      JdbcTemplate jdbcTemplate,
      RegistryMetrics registryMetrics,
      StoreConfigurationProperties storeConfigurationProperties) {
    this(
        platformTransactionManager,
        jdbcTemplate,
        registryMetrics,
        storeConfigurationProperties,
        storeConfigurationProperties.getTransformationCacheBufferSize());
  }

  @VisibleForTesting
  PgTransformationCache(
      @NonNull PlatformTransactionManager platformTransactionManager,
      @NonNull JdbcTemplate jdbcTemplate,
      @NonNull RegistryMetrics registryMetrics,
      @NonNull StoreConfigurationProperties storeConfigurationProperties,
      int maxBufferSize) {
    this.platformTransactionManager = platformTransactionManager;
    this.jdbcTemplate = jdbcTemplate;
    this.registryMetrics = registryMetrics;
    this.storeConfigurationProperties = storeConfigurationProperties;

    registryMetrics.monitor(tpe, "transformation-cache");

    this.maxBufferSize =
        Math.min(maxBufferSize, 9999); // the batchUpdates used only support up to 10k
    this.buffer = new CacheBuffer(registryMetrics);
    this.bufferThreshold = (THRESHOLD_PERCENT * this.maxBufferSize) / 100;
  }

  @Override
  public void put(@NonNull TransformationCache.Key key, @NonNull PgFact f) {
    registerWrite(key, f);
  }

  @Override
  public Optional<PgFact> find(Key key) {

    PgFact factFromBuffer = buffer.get(key);
    if (factFromBuffer != null) {
      registryMetrics.count(EVENT.TRANSFORMATION_CACHE_HIT);
      return Optional.of(factFromBuffer);
    }

    List<PgFact> facts =
        jdbcTemplate.query(selectViaFunction(new String[] {key.id()}), new PgFactRowMapper());

    if (facts.isEmpty()) {
      registryMetrics.count(EVENT.TRANSFORMATION_CACHE_MISS);
      return Optional.empty();
    } else {
      registryMetrics.count(EVENT.TRANSFORMATION_CACHE_HIT);
      return Optional.of(facts.get(0));
    }
  }

  @Override
  public Set<PgFact> findAll(Collection<Key> keysToFind) {

    ArrayList<Key> keys = Lists.newArrayList(keysToFind);

    List<PgFact> facts = new ArrayList<>();
    Iterator<Key> iterator = keys.iterator();
    while (iterator.hasNext()) {
      Key key = iterator.next();
      PgFact found = buffer.get(key);
      if (found != null) {
        iterator.remove();
        facts.add(found);
      }
    }

    if (!keys.isEmpty()) {

      facts.addAll(
          jdbcTemplate.query(
              selectViaFunction(keys.stream().map(Key::id).toArray(String[]::new)),
              new PgFactRowMapper()));
    }

    int hits = facts.size();
    int misses = keysToFind.size() - hits;
    registryMetrics.increase(EVENT.TRANSFORMATION_CACHE_MISS, misses);
    registryMetrics.increase(EVENT.TRANSFORMATION_CACHE_HIT, hits);

    return Sets.newHashSet(facts);
  }

  @NotNull
  static PreparedStatementCreator selectViaFunction(String[] keys) {
    return con -> {
      PreparedStatement ps =
          con.prepareStatement("select header, payload from selectTransformations( ? )");
      Array idArray = con.createArrayOf("varchar", keys);
      ps.setArray(1, idArray);
      return ps;
    };
  }

  @VisibleForTesting
  CompletableFuture<Void> registerWrite(@NonNull TransformationCache.Key key, @NonNull PgFact f) {
    buffer.put(key, f);
    return flushIfNecessary();
  }

  @VisibleForTesting
  @SneakyThrows
  CompletableFuture<Void> flushIfNecessary() {
    final var size = buffer.size();

    if (size >= maxBufferSize) {
      // make sure it does not exceed maxBufferSize
      flush();
      return COMPLETED_FUTURE;
    } else {
      // try to do it async if not already scheduled
      synchronized (tpe) {
        if (size >= bufferThreshold && tpe.getQueue().isEmpty()) {
          return CompletableFuture.runAsync(this::flush, tpe);
        } else {
          return COMPLETED_FUTURE;
        }
      }
    }
  }

  @Override
  public void compact(@NonNull ZonedDateTime thresholdDate) {
    if (!storeConfigurationProperties.isReadOnlyModeEnabled()) {
      flush();

      registryMetrics.timed(
          OP.COMPACT_TRANSFORMATION_CACHE,
          () ->
              inTransactionWithLock(
                  () -> {
                    Timestamp d = Timestamp.from(thresholdDate.toInstant());
                    // will cascade down to tc_access
                    jdbcTemplate.update(
                        "DELETE FROM transformationcache WHERE cache_key in (SELECT cache_key FROM transformationcache_access WHERE last_access < ?)",
                        d);
                  }));
    }
  }

  @Override
  public void invalidateTransformationFor(String ns, String type) {
    // we need to flush even if we're in read only mode in order to prevent a buffer overflow
    flush();

    if (!storeConfigurationProperties.isReadOnlyModeEnabled()) {
      // it is fine if flush worked in another transaction, it just has to be serialized
      inTransactionWithLock(
          () ->
              // will cascade down to tc_access
              jdbcTemplate.update(
                  "DELETE FROM transformationcache WHERE header ->> 'ns' = ? AND header ->> 'type' = ?",
                  ns,
                  type));
    }
  }

  @Override
  public void invalidateTransformationFor(UUID factId) {
    // we need to flush even if we're in read only mode in order to prevent a buffer overflow
    flush();

    if (!storeConfigurationProperties.isReadOnlyModeEnabled()) {
      final var cacheKeySearchString = factId.toString() + "%";
      // it is fine if flush worked in another transaction, it just has to be serialized
      inTransactionWithLock(
          () ->
              // will cascade down to tc_access
              jdbcTemplate.update(
                  "DELETE FROM transformationcache WHERE cache_key LIKE ?", cacheKeySearchString));
    }
  }

  @Scheduled(fixedRate = 10, timeUnit = TimeUnit.MINUTES)
  public void flush() {
    // Before flushing, the buffer is wiped and again open for business.
    // Until the flush is done, a copy of the buffer can be used to read from.
    // Note that this is important even in readonly mode, as otherwise we'd run short on memory
    buffer.clearAfter(
        copy -> {
          if (!copy.isEmpty() && !storeConfigurationProperties.isReadOnlyModeEnabled()) {
            // we want to serialize flushing beyond instances in order to avoid parallel
            // updates/insertions/deletions causing deadlocks
            try {
              inTransactionWithLock(() -> insertBufferedTransformations(copy));
            } catch (Exception e) {
              log.error(
                  "Could not complete batch update of transformations on transformation cache.", e);
            }
          }
        });
  }

  /**
   * this is used to serialize writes to the table in order to prevent circular row lock situations
   * leading to #3279
   */
  @VisibleForTesting
  void inTransactionWithLock(@NonNull Runnable o) {
    new TransactionTemplate(platformTransactionManager)
        // will join an existing tx, or create and commit a new one
        .execute(
            status -> {
              // we're using share mode here in order not to block reads from happening
              jdbcTemplate.execute("LOCK TABLE transformationcache IN EXCLUSIVE MODE");
              o.run();
              return null;
            });
  }

  @VisibleForTesting
  void insertBufferedTransformations(Map<Key, Fact> copy) {
    List<Object[]> parameters =
        copy.entrySet().stream()
            .filter(e -> e.getValue() != null)
            .map(
                p ->
                    new Object[] {
                      p.getKey().id(), p.getValue().jsonHeader(), p.getValue().jsonPayload()
                    })
            .toList();

    if (!parameters.isEmpty()) {
      new TransactionTemplate(platformTransactionManager)
          // will join an existing tx, or create and commit a new one
          .execute(
              status -> {

                // dup-keys can be ignored, in case another node just did the same
                jdbcTemplate.batchUpdate(
                    "INSERT INTO transformationcache (cache_key, header, payload) VALUES (?, ? :: JSONB, ? ::"
                        + " JSONB) ON CONFLICT(cache_key) DO NOTHING",
                    parameters);

                jdbcTemplate.batchUpdate(
                    "INSERT INTO transformationcache_access(cache_key,last_access) VALUES(?,current_date) "
                        + "ON CONFLICT(cache_key) DO UPDATE SET last_access=current_date "
                        + "WHERE excluded.cache_key=? AND transformationcache_access.last_access < current_date",
                    parameters.stream().map(o -> new Object[] {o[0], o[0]}).toList());

                return null;
              });
    }
  }

  @Override
  public void close() throws Exception {
    tpe.shutdownNow();
  }
}
