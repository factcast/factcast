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
package org.factcast.store.pgsql.registry.validation.schema.store;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.factcast.store.pgsql.registry.metrics.MetricEvent;
import org.factcast.store.pgsql.registry.metrics.RegistryMetrics;
import org.factcast.store.pgsql.registry.validation.schema.SchemaConflictException;
import org.factcast.store.pgsql.registry.validation.schema.SchemaKey;
import org.factcast.store.pgsql.registry.validation.schema.SchemaSource;
import org.factcast.store.pgsql.registry.validation.schema.SchemaStore;

import io.micrometer.core.instrument.Tags;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * @author uwe
 */
@RequiredArgsConstructor
public class InMemSchemaStoreImpl implements SchemaStore {
    private final RegistryMetrics registryMetrics;

    private final Map<String, String> id2hashMap = new HashMap<>();

    private final Map<SchemaKey, String> schemaMap = new HashMap<>();

    private Object mutex = new Object();

    @Override
    public void register(@NonNull SchemaSource source, @NonNull String schema)
            throws SchemaConflictException {
        synchronized (mutex) {
            String oldHash = id2hashMap.putIfAbsent(source.id(), source.hash());
            if (oldHash != null && !oldHash.contentEquals(source.hash())) {
                registryMetrics.count(MetricEvent.SCHEMA_CONFLICT, Tags.of(
                        RegistryMetrics.TAG_IDENTITY_KEY, source.id()));

                throw new SchemaConflictException("Key " + source
                        + " does not match the stored hash "
                        + oldHash);
            }

            schemaMap.put(source.toKey(), schema);
        }
    }

    @Override
    public boolean contains(@NonNull SchemaSource source)
            throws SchemaConflictException {
        synchronized (mutex) {
            String hash = id2hashMap.get(source.id());
            if (hash != null)
                if (hash.equals(source.hash()))
                    return true;
                else {
                    registryMetrics.count(MetricEvent.SCHEMA_CONFLICT, Tags.of(
                            RegistryMetrics.TAG_IDENTITY_KEY, source.id()));

                    throw new SchemaConflictException(
                            "SchemaSource at " + source + " does not match the stored hash "
                                    + hash);
                }
            else
                return false;
        }

    }

    @Override
    public Optional<String> get(@NonNull SchemaKey key) {
        synchronized (mutex) {
            return Optional.ofNullable(schemaMap.get(key));
        }
    }

}
