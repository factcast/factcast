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
package org.factcast.store.pgsql.registry.transformation.cache;

import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.UUID;

import org.apache.commons.collections4.map.LRUMap;
import org.factcast.core.Fact;
import org.factcast.store.pgsql.registry.metrics.MetricEvent;
import org.factcast.store.pgsql.registry.metrics.RegistryMetrics;
import org.factcast.store.pgsql.registry.metrics.TimedOperation;
import org.joda.time.DateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NonNull;

public class InMemTransformationCache implements TransformationCache {
    private final RegistryMetrics registryMetrics;

    // very low, but ok for tests
    private static final int DEFAULT_CAPACITY = 1000;

    private Map<String, FactAndAccessTime> cache;

    public InMemTransformationCache(RegistryMetrics registryMetrics) {
        this(DEFAULT_CAPACITY, registryMetrics);
    }

    public InMemTransformationCache(int capacity, RegistryMetrics registryMetrics) {
        cache = new LRUMap<String, FactAndAccessTime>(Math.max(capacity, DEFAULT_CAPACITY));
        this.registryMetrics = registryMetrics;
    }

    @Override
    public void put(@NonNull Fact f, @NonNull String transformationChainId) {
        String key = CacheKey.of(f, transformationChainId);
        synchronized (cache) {
            cache.put(key, new FactAndAccessTime(f, System.currentTimeMillis()));
        }
    }

    @Override
    public Optional<Fact> find(@NonNull UUID eventId, int version,
            @NonNull String transformationChainId) {
        String key = CacheKey.of(eventId, version, transformationChainId);
        Optional<FactAndAccessTime> cached;
        synchronized (cache) {
            cached = Optional.ofNullable(cache.get(key));
        }
        cached.ifPresent(faat -> faat.accessTime(System.currentTimeMillis()));
        registryMetrics.count(cached.isPresent() ? MetricEvent.TRANSFORMATION_CACHE_HIT
                : MetricEvent.TRANSFORMATION_CACHE_MISS);
        return cached.map(FactAndAccessTime::fact);
    }

    @Override
    public void compact(@NonNull DateTime thresholdDate) {
        registryMetrics.timed(TimedOperation.COMPACT_TRANSFORMATION_CACHE, () -> {

            HashSet<Entry<String, FactAndAccessTime>> copyOfEntries;
            synchronized (cache) {
                copyOfEntries = new HashSet<>(cache.entrySet());
            }

            copyOfEntries.forEach(e -> {
                FactAndAccessTime faat = e.getValue();
                if (thresholdDate.isAfter(faat.accessTime))
                    cache.remove(e.getKey());
            });
        });
    }

    @Data
    @AllArgsConstructor
    private static class FactAndAccessTime {
        Fact fact;

        long accessTime;
    }

}
