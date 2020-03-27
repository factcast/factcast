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
package org.factcast.store.pgsql.registry.transformation.store;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.factcast.store.pgsql.registry.metrics.MetricEvent;
import org.factcast.store.pgsql.registry.metrics.RegistryMetrics;
import org.factcast.store.pgsql.registry.transformation.SingleTransformation;
import org.factcast.store.pgsql.registry.transformation.Transformation;
import org.factcast.store.pgsql.registry.transformation.TransformationConflictException;
import org.factcast.store.pgsql.registry.transformation.TransformationKey;
import org.factcast.store.pgsql.registry.transformation.TransformationSource;

import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class InMemTransformationStoreImpl extends AbstractTransformationStore {
    private final RegistryMetrics registryMetrics;

    private final Map<String, String> id2hashMap = new HashMap<>();

    private final Map<TransformationKey, List<Transformation>> transformationCache = new HashMap<>();

    @Override
    protected void doStore(@NonNull TransformationSource source, String transformation)
            throws TransformationConflictException {
        synchronized (mutex) {
            String oldHash = id2hashMap.putIfAbsent(source.id(), source.hash());
            if (oldHash != null && !oldHash.contentEquals(source.hash())) {
                registryMetrics.count(MetricEvent.TRANSFORMATION_CONFLICT, Tags.of(Tag.of(
                        RegistryMetrics.TAG_IDENTITY_KEY, source.id())));

                throw new TransformationConflictException("Key " + source
                        + " does not match the stored hash " + oldHash);
            }

            List<Transformation> transformations = get(source.toKey());

            transformations.add(SingleTransformation.of(source, transformation));
        }
    }

    @Override
    public boolean contains(@NonNull TransformationSource source)
            throws TransformationConflictException {
        synchronized (mutex) {
            String hash = id2hashMap.get(source.id());
            if (hash != null)
                if (hash.equals(source.hash()))
                    return true;
                else {
                    registryMetrics.count(MetricEvent.TRANSFORMATION_CONFLICT, Tags.of(Tag.of(
                            RegistryMetrics.TAG_IDENTITY_KEY, source.id())));

                    throw new TransformationConflictException(
                            "TransformationSource at " + source + " does not match the stored hash "
                                    + hash);
                }
            else
                return false;
        }
    }

    private final Object mutex = new Object();

    @Override
    public List<Transformation> get(@NonNull TransformationKey key) {
        synchronized (mutex) {
            return transformationCache.computeIfAbsent(key, (k) -> new CopyOnWriteArrayList<>());
        }
    }
}
