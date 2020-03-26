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

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.apache.commons.collections15.map.LRUMap;
import org.factcast.core.Fact;
import org.factcast.store.pgsql.registry.metrics.MetricEvent;
import org.factcast.store.pgsql.registry.metrics.RegistryMetrics;
import org.joda.time.DateTime;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class InMemTransformationCache implements TransformationCache {
    private final RegistryMetrics registryMetrics;

    private static final int CAPACITY = 1_000_000;

    private Map<String, Fact> cache = new LRUMap<>(
            InMemTransformationCache.CAPACITY);

    @Override
    public void put(@NonNull Fact f, @NonNull String transformationChainId) {
        String key = CacheKey.of(f, transformationChainId);
        cache.put(key, f);
    }

    @Override
    public Optional<Fact> find(@NonNull UUID eventId, int version,
            @NonNull String transformationChainId) {
        String key = CacheKey.of(eventId, version, transformationChainId);

        Optional<Fact> result = Optional.ofNullable(cache.get(key));

        registryMetrics.count(result.isPresent() ? MetricEvent.TRANSFORMATION_CACHE_HIT
                : MetricEvent.TRANSFORMATION_CACHE_MISS);

        return result;
    }

    @Override
    public void compact(@NonNull DateTime thresholdDate) {
        cache.clear();
    }

}
