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

import java.time.ZonedDateTime;
import java.util.*;
import java.util.Map.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.map.LRUMap;
import org.factcast.core.Fact;
import org.factcast.store.registry.metrics.RegistryMetrics;

@Slf4j
public class InMemTransformationCache implements TransformationCache {
  private final RegistryMetrics registryMetrics;

  // very low, but ok for tests
  private static final int DEFAULT_CAPACITY = 1000;

  private final Map<Key, FactAndAccessTime> cache;

  public InMemTransformationCache(RegistryMetrics registryMetrics) {
    this(DEFAULT_CAPACITY, registryMetrics);
  }

  public InMemTransformationCache(int capacity, RegistryMetrics registryMetrics) {
    cache = Collections.synchronizedMap(new LRUMap<>(Math.max(capacity, DEFAULT_CAPACITY)));
    this.registryMetrics = registryMetrics;
  }

  @Override
  public void put(@NonNull TransformationCache.Key key, @NonNull Fact f) {
    cache.put(key, new FactAndAccessTime(f, System.currentTimeMillis()));
  }

  @Override
  public Optional<Fact> find(@NonNull TransformationCache.Key key) {
    Optional<FactAndAccessTime> cached;
    cached = Optional.ofNullable(cache.get(key));
    cached.ifPresent(faat -> faat.accessTimeInMillis(System.currentTimeMillis()));
    registryMetrics.count(
        cached.isPresent()
            ? RegistryMetrics.EVENT.TRANSFORMATION_CACHE_HIT
            : RegistryMetrics.EVENT.TRANSFORMATION_CACHE_MISS);
    return cached.map(FactAndAccessTime::fact);
  }

  @Override
  public Set<Fact> findAll(Collection<Key> keys) {
    Set<Fact> found = new HashSet<>(keys.size());
    keys.forEach(
        k -> {
          FactAndAccessTime factAndAccessTime = cache.get(k);
          if (factAndAccessTime != null) found.add(factAndAccessTime.fact);
        });

    var hits = found.size();
    var misses = keys.size() - hits;

    if (hits > 0) registryMetrics.increase(RegistryMetrics.EVENT.TRANSFORMATION_CACHE_HIT, hits);
    if (misses > 0)
      registryMetrics.increase(RegistryMetrics.EVENT.TRANSFORMATION_CACHE_MISS, misses);

    return found;
  }

  @Override
  public void compact(@NonNull ZonedDateTime thresholdDate) {
    registryMetrics.timed(
        RegistryMetrics.OP.COMPACT_TRANSFORMATION_CACHE,
        () -> {
          HashSet<Entry<Key, FactAndAccessTime>> copyOfEntries;
          synchronized (cache) {
            copyOfEntries = new HashSet<>(cache.entrySet());
          }

          var thresholdMillis = thresholdDate.toInstant().toEpochMilli();

          copyOfEntries.forEach(
              e -> {
                FactAndAccessTime faat = e.getValue();
                if (thresholdMillis > faat.accessTimeInMillis) {
                  cache.remove(e.getKey());
                }
              });
        });
  }

  @Data
  @AllArgsConstructor
  private static class FactAndAccessTime {
    Fact fact;

    long accessTimeInMillis;
  }
}
