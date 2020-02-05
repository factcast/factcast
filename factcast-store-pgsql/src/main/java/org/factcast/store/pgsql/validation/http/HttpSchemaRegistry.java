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
package org.factcast.store.pgsql.validation.http;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.factcast.store.pgsql.validation.schema.SchemaKey;
import org.factcast.store.pgsql.validation.schema.SchemaRegistry;
import org.factcast.store.pgsql.validation.schema.SchemaRegistryUnavailableException;
import org.factcast.store.pgsql.validation.schema.SchemaSource;
import org.factcast.store.pgsql.validation.schema.SchemaStore;
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
    private SchemaFetcher schemaFetcher;

    @NonNull
    private final SchemaStore store;

    public HttpSchemaRegistry(@NonNull URL baseUrl, @NonNull SchemaStore store) {
        this.store = store;
        this.indexFetcher = new IndexFetcher(baseUrl);
        this.schemaFetcher = new SchemaFetcher(baseUrl);
    }

    @VisibleForTesting
    protected HttpSchemaRegistry(@NonNull SchemaStore store, @NonNull IndexFetcher indexFetcher,
            @NonNull SchemaFetcher schemaFetcher) {
        this.store = store;
        this.indexFetcher = indexFetcher;
        this.schemaFetcher = schemaFetcher;
    }

    private LoadingCache<SchemaKey, JsonSchema> cache = new AbstractLoadingCache<SchemaKey, JsonSchema>() {

        @Override
        public JsonSchema get(SchemaKey key) throws ExecutionException {
            return store.get(key).map(createSchema()).get();
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
            return store.get(key).map(createSchema()).orElse(null);
        }
    };

    private final Object mutex = new Object();

    @Override
    public void refreshVerbose() {

        synchronized (mutex) {

            Stopwatch sw = Stopwatch.createStarted();
            log.info("SchemaStore update started");
            refreshSilent();
            log.info("SchemaStore update finished in {}ms", sw.stop()
                    .elapsed(TimeUnit.MILLISECONDS));

            // otherwise just return
        }
    }

    @Override
    public void refreshSilent() {
        synchronized (mutex) {

            Optional<RegistryIndex> fetchIndex = indexFetcher.fetchIndex();
            if (fetchIndex.isPresent()) {
                process(fetchIndex.get());
            }
        }
    }

    private void process(RegistryIndex index) {
        List<SchemaSource> toFetch = index.schemes()
                .stream()
                .filter(source -> !store.contains(source))
                .collect(Collectors.toList());
        if (toFetch.isEmpty()) {
            log.info("Schemaregistry is up to date.");
        } else {
            int count = toFetch.size();
            log.info("SchemaStore will be updated, {} {} to fetch.", count, count == 1 ? "schema"
                    : "schemes");
            toFetch.parallelStream().forEach(source -> {
                try {
                    store.register(source, schemaFetcher.fetch(source));
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

}
