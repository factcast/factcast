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
package org.factcast.store.pgsql.registry.http;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.factcast.store.pgsql.registry.SchemaRegistry;
import org.factcast.store.pgsql.registry.transformation.Transformation;
import org.factcast.store.pgsql.registry.transformation.TransformationKey;
import org.factcast.store.pgsql.registry.transformation.TransformationSource;
import org.factcast.store.pgsql.registry.transformation.TransformationStore;
import org.factcast.store.pgsql.registry.transformation.TransformationStoreListener;
import org.factcast.store.pgsql.registry.validation.schema.SchemaKey;
import org.factcast.store.pgsql.registry.validation.schema.SchemaSource;
import org.factcast.store.pgsql.registry.validation.schema.SchemaStore;
import org.springframework.stereotype.Component;

import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.github.fge.jsonschema.main.JsonSchema;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Stopwatch;
import com.google.common.cache.AbstractLoadingCache;
import com.google.common.cache.LoadingCache;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component

public class HttpSchemaRegistry implements SchemaRegistry {
    @NonNull
    private IndexFetcher indexFetcher;

    @NonNull
    private RegistryFileFetcher registryFileFetcher;

    @NonNull
    private final SchemaStore schemaStore;

    @NonNull
    private final TransformationStore transformationStore;

    public HttpSchemaRegistry(@NonNull URL baseUrl, @NonNull SchemaStore schemaStore,
            @NonNull TransformationStore transformationStore) {
        this.schemaStore = schemaStore;
        this.transformationStore = transformationStore;
        this.indexFetcher = new IndexFetcher(baseUrl);
        this.registryFileFetcher = new RegistryFileFetcher(baseUrl);
    }

    @VisibleForTesting
    protected HttpSchemaRegistry(@NonNull SchemaStore schemaStore,
            @NonNull TransformationStore transformationStore,
            @NonNull IndexFetcher indexFetcher, @NonNull RegistryFileFetcher registryFileFetcher) {
        this.schemaStore = schemaStore;
        this.transformationStore = transformationStore;
        this.indexFetcher = indexFetcher;
        this.registryFileFetcher = registryFileFetcher;
    }

    private LoadingCache<SchemaKey, JsonSchema> cache = new AbstractLoadingCache<SchemaKey, JsonSchema>() {

        @Override
        public JsonSchema get(SchemaKey key) throws ExecutionException {
            return schemaStore.get(key).map(createSchema()).get();
        }

        private Function<? super String, ? extends JsonSchema> createSchema() {
            return s -> {
                try {
                    return ValidationConstants.factory.getJsonSchema(
                            ValidationConstants.objectMapper.readTree(s));
                } catch (ProcessingException | IOException e) {
                    throw new IllegalArgumentException("Cannot create schema from : \n " + s, e);
                }
            };
        }

        @Override
        public @Nullable JsonSchema getIfPresent(Object k) {
            SchemaKey key = (SchemaKey) k;
            return schemaStore.get(key).map(createSchema()).orElse(null);
        }
    };

    private final Object mutex = new Object();

    @Override
    public void refreshVerbose() {

        synchronized (mutex) {

            Stopwatch sw = Stopwatch.createStarted();
            log.info("Registry update started");
            refreshSilent();
            log.info("Registry update finished in {}ms", sw.stop().elapsed(TimeUnit.MILLISECONDS));

        }
    }

    @Override
    public void refreshSilent() {
        synchronized (mutex) {

            Optional<RegistryIndex> fetchIndex = indexFetcher.fetchIndex();
            if (fetchIndex.isPresent()) {
                process(fetchIndex.get());
            }
            // otherwise just return
        }
    }

    private void process(RegistryIndex index) {
        updateSchemes(index);
        updateTransformations(index);
    }

    private void updateSchemes(RegistryIndex index) {
        List<SchemaSource> toFetch = index.schemes()
                .stream()
                .filter(source -> !schemaStore.contains(source))
                .collect(Collectors.toList());
        if (toFetch.isEmpty()) {
            log.info("Schemaregistry is up to date.");
        } else {
            int count = toFetch.size();
            log.info("SchemaStore will be updated, {} {} to fetch.", count, count == 1 ? "schema"
                    : "schemes");
            toFetch.parallelStream().forEach(source -> {
                try {
                    schemaStore.register(source, registryFileFetcher.fetchSchema(source));
                } catch (IOException e) {
                    throw new SchemaRegistryUnavailableException(e);
                }
            });
        }
    }

    private void updateTransformations(RegistryIndex index) {
        List<TransformationSource> toFetch = index.transformations()
                .stream()
                .filter(source -> !transformationStore.contains(source))
                .collect(Collectors.toList());

        if (toFetch.isEmpty()) {
            log.info("Transformationregistry is up to date.");
        } else {
            int count = toFetch.size();
            log.info("TransformationStore will be updated, {} {} to fetch.", count,
                    count == 1 ? "transformation" : "transformations");
            toFetch.parallelStream().forEach(source -> {
                try {
                    String transformationCode = null;
                    if (!source.isSynthetic()) {
                        transformationCode = registryFileFetcher.fetchTransformation(source);
                    }

                    transformationStore.store(source, transformationCode);
                } catch (IOException e) {
                    throw new SchemaRegistryUnavailableException(e);
                }
            });
        }
    }

    @Override
    public Optional<JsonSchema> get(SchemaKey key) {
        return Optional.ofNullable(cache.getIfPresent(key));
    }

    @Override
    public List<Transformation> get(TransformationKey key) {
        return transformationStore.get(key);
    }

    @Override
    public void register(TransformationStoreListener listener) {
        transformationStore.register(listener);
    }

}
