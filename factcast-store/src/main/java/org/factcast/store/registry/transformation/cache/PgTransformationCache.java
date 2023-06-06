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
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.Fact;
import org.factcast.store.registry.metrics.RegistryMetrics;
import org.factcast.store.registry.metrics.RegistryMetrics.EVENT;
import org.factcast.store.registry.metrics.RegistryMetrics.OP;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.scheduling.annotation.Scheduled;

@Slf4j
public class PgTransformationCache implements TransformationCache, AutoCloseable {
  private static final int MAX_BATCH_SIZE = 20_000;
  private final JdbcTemplate jdbcTemplate;
  private final NamedParameterJdbcTemplate namedJdbcTemplate;
  private final RegistryMetrics registryMetrics;

  private final ExecutorService es;

  private static final CompletableFuture<Void> COMPLETED_FUTURE =
      CompletableFuture.completedFuture(null);

  @Getter(AccessLevel.PROTECTED)
  @VisibleForTesting
  /* entry of null means read, entry of non-null means write */
  private final CacheBuffer buffer = new CacheBuffer();

  private int maxBufferSize = 1000;

  public PgTransformationCache(
      JdbcTemplate jdbcTemplate,
      NamedParameterJdbcTemplate namedJdbcTemplate,
      RegistryMetrics registryMetrics) {
    this.jdbcTemplate = jdbcTemplate;
    this.namedJdbcTemplate = namedJdbcTemplate;
    this.registryMetrics = registryMetrics;
    this.es = registryMetrics.monitor(Executors.newSingleThreadExecutor(), "transformation-cache");
  }

  @VisibleForTesting
  PgTransformationCache(
      JdbcTemplate jdbcTemplate,
      NamedParameterJdbcTemplate namedJdbcTemplate,
      RegistryMetrics registryMetrics,
      int maxBufferSize) {
    this.jdbcTemplate = jdbcTemplate;
    this.namedJdbcTemplate = namedJdbcTemplate;
    this.registryMetrics = registryMetrics;
    this.maxBufferSize = maxBufferSize;
    this.es = Executors.newSingleThreadExecutor();
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
  CompletableFuture<Void> flushIfNecessary() {
    if (buffer.size() >= maxBufferSize) {
      return CompletableFuture.runAsync(this::flush, es);
    } else {
      return COMPLETED_FUTURE;
    }
  }

  @Override
  public void compact(@NonNull ZonedDateTime thresholdDate) {
    flush();

    registryMetrics.timed(
        OP.COMPACT_TRANSFORMATION_CACHE,
        () ->
            jdbcTemplate.update(
                "DELETE FROM transformationcache WHERE last_access < ?",
                new Date(thresholdDate.toInstant().toEpochMilli())));
  }

  @Override
  public void invalidateTransformationFor(String ns, String type) {
    flush();
    jdbcTemplate.update(
        "DELETE FROM transformationcache WHERE header ->> 'ns' = ? AND header ->> 'type' = ?",
        ns,
        type);
  }

  @Scheduled(fixedRate = 10, timeUnit = TimeUnit.MINUTES)
  public void flush() {
    Map<Key, Fact> copy = buffer.clear();
    if (!copy.isEmpty()) {
      try {
        insertBufferedTransformations(copy);
        insertBufferedAccesses(copy);
      } catch (Exception e) {
        log.error("Could not complete batch update of transformations on transformation cache.", e);
      }
    }
  }

  @VisibleForTesting
  void clearAndFlushAccessesOnly() {
    Map<Key, Fact> copy = buffer.clear();
    if (!copy.isEmpty()) {
      try {
        insertBufferedAccesses(copy);
      } catch (Exception e) {
        log.error(
            "Could not complete batch update of transformation accesses on transformation cache.",
            e);
      }
    }
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
    es.shutdown();
  }
}
