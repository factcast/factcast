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

import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.map.LRUMap;
import org.factcast.store.internal.PgFact;
import org.factcast.store.registry.metrics.RegistryMetrics;

@Slf4j
public class InMemTransformationCache implements TransformationCache {
  private final RegistryMetrics registryMetrics;

  // very low, but ok for tests
  private static final int DEFAULT_CAPACITY = 100;

  private final Map<Key, PgFact> cache;

  public InMemTransformationCache(RegistryMetrics registryMetrics) {
    this(DEFAULT_CAPACITY, registryMetrics);
  }

  public InMemTransformationCache(int capacity, RegistryMetrics registryMetrics) {
    cache = Collections.synchronizedMap(new LRUMap<>(Math.max(capacity, DEFAULT_CAPACITY)));
    this.registryMetrics = registryMetrics;
  }

  @Override
  public void put(@NonNull TransformationCache.Key key, @NonNull PgFact f) {
    cache.put(key, f);
  }

  @Override
  public Optional<PgFact> find(@NonNull TransformationCache.Key key) {
    Optional<PgFact> cached = Optional.ofNullable(cache.get(key));
    registryMetrics.count(
        cached.isPresent()
            ? RegistryMetrics.EVENT.TRANSFORMATION_CACHE_HIT
            : RegistryMetrics.EVENT.TRANSFORMATION_CACHE_MISS);
    return cached;
  }

  @Override
  public Set<PgFact> findAll(Collection<Key> keys) {
    Set<PgFact> found = new HashSet<>(keys.size());
    keys.forEach(
        k -> {
          PgFact fact = cache.get(k);
          if (fact != null) {
            found.add(fact);
          }
        });

    var hits = found.size();
    var misses = keys.size() - hits;

    if (hits > 0) {
      registryMetrics.increase(RegistryMetrics.EVENT.TRANSFORMATION_CACHE_HIT, hits);
    }
    if (misses > 0) {
      registryMetrics.increase(RegistryMetrics.EVENT.TRANSFORMATION_CACHE_MISS, misses);
    }

    return found;
  }

  @Override
  public void invalidateTransformationFor(String ns, String type) {
    synchronized (cache) {
      Set<Key> toBeInvalidated =
          cache.entrySet().stream()
              .filter(
                  e ->
                      e.getValue().ns().equals(ns)
                          && Objects.equals(e.getValue().type(), type))
              .map(Entry::getKey)
              .collect(Collectors.toSet());
      if (!toBeInvalidated.isEmpty()) {
        toBeInvalidated.forEach(cache::remove);
      }
    }
  }

  @Override
  public void invalidateTransformationFor(UUID factId) {
    synchronized (cache) {
      Set<Key> toBeInvalidated =
          cache.keySet().stream()
              .filter(k -> factId.equals(k.factId()))
              .collect(Collectors.toSet());
      if (!toBeInvalidated.isEmpty()) {
        toBeInvalidated.forEach(cache::remove);
      }
    }
  }

  @Override
  public void flush() {
    // NOP
  }
}
