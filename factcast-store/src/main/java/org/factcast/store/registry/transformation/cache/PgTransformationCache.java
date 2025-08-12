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
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.Fact;
import org.factcast.store.StoreConfigurationProperties;
import org.factcast.store.registry.metrics.RegistryMetrics;
import org.factcast.store.registry.metrics.RegistryMetrics.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
public class PgTransformationCache implements TransformationCache, AutoCloseable {
  private static final int MAX_BATCH_SIZE = 20_000;
  private final JdbcTemplate jdbcTemplate;
  private final NamedParameterJdbcTemplate namedJdbcTemplate;
  private final RegistryMetrics registryMetrics;
  private final StoreConfigurationProperties storeConfigurationProperties;

  private final ThreadPoolExecutor tpe =
      new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>());

  private static final CompletableFuture<Void> COMPLETED_FUTURE =
      CompletableFuture.completedFuture(null);

  @Getter(AccessLevel.PROTECTED)
  @VisibleForTesting
  // entry of null means read, entry of non-null means write
  private final CacheBuffer buffer;

  private final PlatformTransactionManager platformTransactionManager;

  private int bufferThreshold = 1000;

  public final int maxBufferSize;

  public PgTransformationCache(
      PlatformTransactionManager platformTransactionManager,
      JdbcTemplate jdbcTemplate,
      NamedParameterJdbcTemplate namedJdbcTemplate,
      RegistryMetrics registryMetrics,
      StoreConfigurationProperties storeConfigurationProperties) {
    this.platformTransactionManager = platformTransactionManager;
    this.jdbcTemplate = jdbcTemplate;
    this.namedJdbcTemplate = namedJdbcTemplate;
    this.registryMetrics = registryMetrics;
    this.storeConfigurationProperties = storeConfigurationProperties;

    registryMetrics.monitor(tpe, "transformation-cache");

    this.maxBufferSize = bufferThreshold * 30;
    this.buffer = new CacheBuffer(registryMetrics);
  }

  @VisibleForTesting
  PgTransformationCache(
      PlatformTransactionManager platformTransactionManager,
      JdbcTemplate jdbcTemplate,
      NamedParameterJdbcTemplate namedJdbcTemplate,
      RegistryMetrics registryMetrics,
      StoreConfigurationProperties storeConfigurationProperties,
      int bufferThreshold) {
    this.platformTransactionManager = platformTransactionManager;
    this.jdbcTemplate = jdbcTemplate;
    this.namedJdbcTemplate = namedJdbcTemplate;
    this.registryMetrics = registryMetrics;
    this.bufferThreshold = bufferThreshold;
    this.maxBufferSize = bufferThreshold;
    this.storeConfigurationProperties = storeConfigurationProperties;
    this.buffer = new CacheBuffer(registryMetrics);
  }

  @Override
  public void put(@NonNull TransformationCache.Key key, @NonNull Fact f) {
    registerWrite(key, f);
  }

  @Override
  public Optional<Fact> find(Key key) {

    Fact factFromBuffer = buffer.get(key);
    if (factFromBuffer != null) {
      registerAccess(key);
      registryMetrics.count(EVENT.TRANSFORMATION_CACHE_HIT);
      return Optional.of(factFromBuffer);
    }

    List<Fact> facts =
        jdbcTemplate.query(
            "SELECT header, payload FROM transformationcache WHERE cache_key = ?",
            new Object[] {key.id()},
            new FactRowMapper());

    if (facts.isEmpty()) {
      registryMetrics.count(EVENT.TRANSFORMATION_CACHE_MISS);
      return Optional.empty();
    } else {
      registerAccess(key);
      registryMetrics.count(EVENT.TRANSFORMATION_CACHE_HIT);
      return Optional.of(facts.get(0));
    }
  }

  @Override
  public Set<Fact> findAll(Collection<Key> keysToFind) {

    ArrayList<Key> keys = Lists.newArrayList(keysToFind);

    List<Fact> facts = new ArrayList<>();
    Iterator<Key> iterator = keys.iterator();
    while (iterator.hasNext()) {
      Key key = iterator.next();
      Fact found = buffer.get(key);
      if (found != null) {
        iterator.remove();
        facts.add(found);
      }
    }

    if (!keys.isEmpty()) {

      SqlParameterSource parameters =
          new MapSqlParameterSource("ids", keys.stream().map(Key::id).collect(Collectors.toList()));
      facts.addAll(
          namedJdbcTemplate.query(
              "SELECT header, payload FROM transformationcache WHERE cache_key IN (:ids)",
              parameters,
              new FactRowMapper()));
    }

    int hits = facts.size();
    int misses = keysToFind.size() - hits;
    registryMetrics.increase(EVENT.TRANSFORMATION_CACHE_MISS, misses);
    registryMetrics.increase(EVENT.TRANSFORMATION_CACHE_HIT, hits);

    registerAccess(keys);

    return Sets.newHashSet(facts);
  }

  @VisibleForTesting
  CompletableFuture<Void> registerAccess(Collection<Key> keys) {
    buffer.putAllNull(keys);
    return flushIfNecessary();
  }

  @VisibleForTesting
  CompletableFuture<Void> registerAccess(Key cacheKey) {
    buffer.put(cacheKey, null);
    return flushIfNecessary();
  }

  @VisibleForTesting
  CompletableFuture<Void> registerWrite(@NonNull TransformationCache.Key key, @NonNull Fact f) {
    buffer.put(key, f);
    return flushIfNecessary();
  }

  @VisibleForTesting
  @SneakyThrows
  CompletableFuture<Void> flushIfNecessary() {
    final var size = buffer.size();

    if (size > maxBufferSize) {
      flush();
      return COMPLETED_FUTURE;
    } else {
      if (size >= bufferThreshold && tpe.getQueue().isEmpty()) {
        return CompletableFuture.runAsync(this::flush, tpe);
      } else {
        return COMPLETED_FUTURE;
      }
    }
  }

  @Override
  public void compact(@NonNull ZonedDateTime thresholdDate) {
    // we need to flush even if we're in read only mode in order to prevent a buffer overflow
    flush();

    if (!storeConfigurationProperties.isReadOnlyModeEnabled()) {
      // it is fine if flush worked in another transaction, it just has to be serialized
      registryMetrics.timed(
          OP.COMPACT_TRANSFORMATION_CACHE,
          () ->
              inTransactionWithLock(
                  () ->
                      jdbcTemplate.update(
                          "DELETE FROM transformationcache WHERE last_access < ?",
                          new Date(thresholdDate.toInstant().toEpochMilli()))));
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
              inTransactionWithLock(
                  () -> {
                    insertBufferedTransformations(copy);
                    insertBufferedAccesses(copy);
                  });
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
            .collect(Collectors.toList());

    if (!parameters.isEmpty()) {

      // dup-keys can be ignored, in case another node just did the same
      Iterables.partition(parameters, MAX_BATCH_SIZE)
          .forEach(
              p ->
                  jdbcTemplate.batchUpdate(
                      "INSERT INTO transformationcache (cache_key, header, payload) VALUES (?, ? :: JSONB, ? ::"
                          + " JSONB) ON CONFLICT(cache_key) DO NOTHING",
                      p));
    }
  }

  @VisibleForTesting
  void insertBufferedAccesses(Map<Key, Fact> copy) {
    List<String> keys =
        copy.entrySet().stream()
            .filter(e -> e.getValue() == null)
            .map(p -> p.getKey().id())
            .collect(Collectors.toList());

    if (!keys.isEmpty()) {

      Iterables.partition(keys, MAX_BATCH_SIZE)
          .forEach(
              k -> {
                SqlParameterSource parameters = new MapSqlParameterSource("ids", k);
                namedJdbcTemplate.update(
                    "UPDATE transformationcache SET last_access=now() WHERE cache_key IN (:ids)",
                    parameters);
              });
    }
  }

  @Override
  public void close() throws Exception {
    tpe.shutdown();
  }
}
