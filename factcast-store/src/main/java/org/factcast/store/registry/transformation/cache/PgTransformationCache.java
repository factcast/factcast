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

import static java.util.function.Function.identity;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toMap;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.factcast.core.Fact;
import org.factcast.store.registry.metrics.RegistryMetrics;
import org.factcast.store.registry.metrics.RegistryMetrics.EVENT;
import org.factcast.store.registry.metrics.RegistryMetrics.OP;
import org.joda.time.DateTime;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class PgTransformationCache implements TransformationCache {
  private final JdbcTemplate jdbcTemplate;

  private final RegistryMetrics registryMetrics;

  @Override
  public void put(@NonNull Fact fact, @NonNull String transformationChainId) {
    String cacheKey = CacheKey.of(fact, transformationChainId);

    // dup-keys can be ignored, in case another node just did the same

    jdbcTemplate.update(
        "INSERT INTO transformationcache (cache_key, header, payload) VALUES %s ON CONFLICT(cache_key) DO NOTHING",
        cacheKey, fact.jsonHeader(), fact.jsonPayload());
  }

  @Override
  public void put(@NonNull Collection<FactWithTargetVersion> factsWithTargetVersion) {

    if (factsWithTargetVersion.isEmpty()) {
      return;
    }

    List<FactWithCacheKey> cacheKeys =
        factsWithTargetVersion.stream()
            .map(
                f ->
                    new FactWithCacheKey(
                        f.fact(), CacheKey.of(f.fact(), f.transformationChain().id())))
            .collect(Collectors.toList());

    // dup-keys can be ignored, in case another node just did the same
    jdbcTemplate.batchUpdate(
        "INSERT INTO transformationcache (cache_key, header, payload) VALUES (?, ? :: JSONB, ? :: JSONB) ON CONFLICT(cache_key) DO NOTHING",
        new BatchPreparedStatementSetter() {

          public void setValues(PreparedStatement ps, int i) throws SQLException {
            ps.setString(1, cacheKeys.get(i).cacheKey());
            ps.setString(2, cacheKeys.get(i).fact().jsonHeader());
            ps.setString(3, cacheKeys.get(i).fact().jsonPayload());
          }

          public int getBatchSize() {
            return cacheKeys.size();
          }
        });
  }

  @Override
  public Optional<Fact> find(
      @NonNull UUID eventId, int version, @NonNull String transformationChainId) {

    String cacheKey = CacheKey.of(eventId, version, transformationChainId);

    List<Fact> facts =
        jdbcTemplate.query(
            "SELECT header, payload FROM transformationcache WHERE cache_key = ?",
            ((rs, rowNum) -> {
              String header = rs.getString("header");
              String payload = rs.getString("payload");

              return Fact.of(header, payload);
            }),
            new Object[] {cacheKey});

    if (facts.isEmpty()) {
      registryMetrics.count(EVENT.TRANSFORMATION_CACHE_MISS);

      return Optional.empty();
    }

    jdbcTemplate.update(
        "UPDATE transformationcache SET last_access=now() WHERE cache_key = ?", cacheKey);

    registryMetrics.count(EVENT.TRANSFORMATION_CACHE_HIT);

    return Optional.of(facts.get(0));
  }

  @Override
  public Map<FactWithTargetVersion, Fact> find(
      @NonNull Collection<FactWithTargetVersion> factsWithTargetVersion) {

    if (factsWithTargetVersion.isEmpty()) {
      return Collections.emptyMap();
    }

    List<FactWithCacheKey> cachedFactsWithKeys = loadFromCache(factsWithTargetVersion);

    CompletableFuture.runAsync(() -> updateTimestampsOfFactsLoadedFromCache(cachedFactsWithKeys))
        .whenComplete(
            (result, ex) -> {
              if (ex != null) {
                log.error("Error updating timestamps of facts", ex);
              } else {
                log.debug("Finished updating timestamps of facts loaded from cache.");
              }
            });

    var cachedFacts =
        cachedFactsWithKeys.stream() //
            .map(FactWithCacheKey::fact)
            .collect(Collectors.toSet());

    countCacheMisses(factsWithTargetVersion, cachedFacts);
    countCacheHits(cachedFacts);

    return enrichWithTargetVersion(factsWithTargetVersion, cachedFacts);
  }

  @NonNull
  private List<FactWithCacheKey> loadFromCache(
      @NonNull Collection<FactWithTargetVersion> factsWithTargetVersion) {

    Set<String> cacheKeys = generateCacheKeys(factsWithTargetVersion);

    String inSqlQuery = String.join(",", Collections.nCopies(cacheKeys.size(), "?"));
    String querySql =
        String.format(
            "SELECT cache_key, header, payload FROM transformationcache WHERE cache_key in (%s)",
            inSqlQuery);

    return jdbcTemplate.query(
        querySql,
        ((rs, rowNum) -> {
          String cacheKey = rs.getString("cache_key");
          String header = rs.getString("header");
          String payload = rs.getString("payload");

          return new FactWithCacheKey(Fact.of(header, payload), cacheKey);
        }),
        cacheKeys.toArray());
  }

  @NonNull
  private void updateTimestampsOfFactsLoadedFromCache(
      @NonNull List<FactWithCacheKey> cachedFactsWithKeys) {

    String inSqlUpdate = String.join(",", Collections.nCopies(cachedFactsWithKeys.size(), "?"));

    String updateSql =
        String.format(
            "UPDATE transformationcache SET last_access=now() WHERE cache_key in (%s)",
            inSqlUpdate);

    var cachedFactsIds = cachedFactsWithKeys.stream().map(FactWithCacheKey::cacheKey).toArray();

    jdbcTemplate.update(updateSql, cachedFactsIds);
  }

  private void countCacheMisses(
      @NonNull Collection<FactWithTargetVersion> factsWithTargetVersion,
      @NonNull Set<Fact> cachedFacts) {

    factsWithTargetVersion.stream()
        .map(FactWithTargetVersion::fact)
        .filter(not(cachedFacts::contains))
        .forEach(f -> registryMetrics.count(EVENT.TRANSFORMATION_CACHE_MISS));
  }

  private void countCacheHits(@NonNull Set<Fact> cachedFacts) {
    cachedFacts.forEach(f -> registryMetrics.count(EVENT.TRANSFORMATION_CACHE_HIT));
  }

  @NonNull
  private Map<FactWithTargetVersion, Fact> enrichWithTargetVersion(
      @NonNull Collection<FactWithTargetVersion> factsWithTargetVersion,
      @NonNull Set<Fact> cachedFacts) {

    // map for lookup of target version
    var allIdsToFactWithTargetVersion =
        factsWithTargetVersion.stream() //
            .collect(toMap(f -> f.fact().id(), identity()));

    return cachedFacts.stream()
        .collect(toMap(f -> allIdsToFactWithTargetVersion.get(f.id()), identity()));
  }

  @NonNull
  private Set<String> generateCacheKeys(
      @NonNull Collection<FactWithTargetVersion> factsWithTargetVersion) {
    return factsWithTargetVersion.stream()
        .map(f -> CacheKey.of(f.fact().id(), f.fact().version(), f.transformationChain().id()))
        .collect(Collectors.toSet());
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

  @Value
  static class FactWithCacheKey {
    @NonNull Fact fact;
    @NonNull String cacheKey;
  }
}
