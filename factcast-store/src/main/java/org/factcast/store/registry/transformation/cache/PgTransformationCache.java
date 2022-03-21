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
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.Fact;
import org.factcast.store.registry.metrics.RegistryMetrics;
import org.factcast.store.registry.metrics.RegistryMetrics.EVENT;
import org.factcast.store.registry.metrics.RegistryMetrics.OP;
import org.joda.time.DateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;

@RequiredArgsConstructor
@Slf4j
public class PgTransformationCache implements TransformationCache {
  private final JdbcTemplate jdbcTemplate;

  private final RegistryMetrics registryMetrics;

  private List<String> cacheKeysBuffer = Collections.synchronizedList(new ArrayList<>());
  private int maxBufferSize = 1000;

  @VisibleForTesting
  PgTransformationCache(
      JdbcTemplate jdbcTemplate,
      RegistryMetrics registryMetrics,
      List<String> cacheKeysBatch,
      int maxBufferSize) {
    this.jdbcTemplate = jdbcTemplate;
    this.registryMetrics = registryMetrics;
    this.cacheKeysBuffer = cacheKeysBatch;
    this.maxBufferSize = maxBufferSize;
  }

  @Override
  public void put(@NonNull Fact fact, @NonNull String transformationChainId) {
    String cacheKey = CacheKey.of(fact, transformationChainId);

    // dup-keys can be ignored, in case another node just did the same

    jdbcTemplate.update(
        "INSERT INTO transformationcache (cache_key, header, payload) VALUES (?, ? :: JSONB, ? ::"
            + " JSONB) ON CONFLICT(cache_key) DO NOTHING",
        cacheKey,
        fact.jsonHeader(),
        fact.jsonPayload());
  }

  @Override
  public Optional<Fact> find(
      @NonNull UUID eventId, int version, @NonNull String transformationChainId) {
    String cacheKey = CacheKey.of(eventId, version, transformationChainId);

    List<Fact> facts =
        jdbcTemplate.query(
            "SELECT header, payload FROM transformationcache WHERE cache_key = ?",
            new Object[] {cacheKey},
            ((rs, rowNum) -> {
              String header = rs.getString("header");
              String payload = rs.getString("payload");

              return Fact.of(header, payload);
            }));

    if (facts.isEmpty()) {
      registryMetrics.count(EVENT.TRANSFORMATION_CACHE_MISS);

      return Optional.empty();
    }

    cacheKeysBuffer.add(cacheKey);
    if (cacheKeysBuffer.size() >= maxBufferSize) {
      CompletableFuture.runAsync(this::flush);
    }

    registryMetrics.count(EVENT.TRANSFORMATION_CACHE_HIT);

    return Optional.of(facts.get(0));
  }

  @Override
  public void compact(@NonNull DateTime thresholdDate) {
    registryMetrics.timed(
        OP.COMPACT_TRANSFORMATION_CACHE,
        () -> {
          jdbcTemplate.update(
              "DELETE FROM transformationcache WHERE last_access < ?", thresholdDate.toDate());
        });
  }

  @Scheduled(fixedRate = 10, timeUnit = TimeUnit.MINUTES)
  public void flush() {
    List<String> copy;
    synchronized (cacheKeysBuffer) {
      copy = new ArrayList<String>(cacheKeysBuffer);
      cacheKeysBuffer.clear();
    }
    if (!copy.isEmpty()) {
      try {
        jdbcTemplate.batchUpdate(
            "UPDATE transformationcache SET last_access=now() WHERE cache_key = ?",
            copy,
            copy.size(),
            (ps, arg) -> ps.setString(1, arg));
      } catch (RuntimeException e) {
        log.warn(
            "Could not complete batch update last_access on transformation cache. Error: {}",
            e.getMessage());
      }
    }
  }
}
