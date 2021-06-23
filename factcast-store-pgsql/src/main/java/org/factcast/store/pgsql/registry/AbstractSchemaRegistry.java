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
package org.factcast.store.pgsql.registry;

import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.github.fge.jsonschema.main.JsonSchema;
import com.google.common.base.Stopwatch;
import com.google.common.cache.AbstractLoadingCache;
import com.google.common.cache.LoadingCache;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.factcast.store.pgsql.PgConfigurationProperties;
import org.factcast.store.pgsql.registry.http.ValidationConstants;
import org.factcast.store.pgsql.registry.metrics.RegistryMetrics;
import org.factcast.store.pgsql.registry.metrics.RegistryMetrics.EVENT;
import org.factcast.store.pgsql.registry.metrics.RegistryMetrics.OP;
import org.factcast.store.pgsql.registry.transformation.*;
import org.factcast.store.pgsql.registry.validation.schema.SchemaConflictException;
import org.factcast.store.pgsql.registry.validation.schema.SchemaKey;
import org.factcast.store.pgsql.registry.validation.schema.SchemaSource;
import org.factcast.store.pgsql.registry.validation.schema.SchemaStore;

@Slf4j
public abstract class AbstractSchemaRegistry implements SchemaRegistry {
  @NonNull protected final IndexFetcher indexFetcher;

  @NonNull protected final RegistryFileFetcher registryFileFetcher;

  @NonNull protected final SchemaStore schemaStore;

  @NonNull protected final TransformationStore transformationStore;

  @NonNull protected final RegistryMetrics registryMetrics;
  @NonNull protected final PgConfigurationProperties pgConfigurationProperties;

  protected final Object mutex = new Object();

  private final LoadingCache<SchemaKey, JsonSchema> cache =
      new AbstractLoadingCache<SchemaKey, JsonSchema>() {

        @Override
        public JsonSchema get(@NonNull SchemaKey key) {
          return schemaStore.get(key).map(createSchema()).orElse(null);
        }

        private Function<? super String, ? extends JsonSchema> createSchema() {
          return s -> {
            try {
              return ValidationConstants.JSON_SCHEMA_FACTORY.getJsonSchema(
                  ValidationConstants.JACKSON.readTree(s));
            } catch (ProcessingException | IOException e) {
              throw new IllegalArgumentException("Cannot create schema from : \n " + s, e);
            }
          };
        }

        @Override
        public @Nullable JsonSchema getIfPresent(@NonNull Object k) {
          SchemaKey key = (SchemaKey) k;
          return schemaStore.get(key).map(createSchema()).orElse(null);
        }
      };

  public AbstractSchemaRegistry(
      @NonNull IndexFetcher indexFetcher,
      @NonNull RegistryFileFetcher registryFileFetcher,
      @NonNull SchemaStore schemaStore,
      @NonNull TransformationStore transformationStore,
      @NonNull RegistryMetrics registryMetrics,
      @NonNull PgConfigurationProperties pgConfigurationProperties) {
    this.indexFetcher = indexFetcher;
    this.registryFileFetcher = registryFileFetcher;
    this.schemaStore = schemaStore;
    this.transformationStore = transformationStore;
    this.registryMetrics = registryMetrics;
    this.pgConfigurationProperties = pgConfigurationProperties;
  }

  @Override
  public void fetchInitial() {

    synchronized (mutex) {
      Stopwatch sw = Stopwatch.createStarted();
      log.info("Registry update started");
      try {
        indexFetcher.fetchIndex().ifPresent(this::process);
        log.info("Registry update finished in {}ms", sw.stop().elapsed(TimeUnit.MILLISECONDS));
      } catch (Throwable e) {
        log.warn("While fetching initial state of registry", e);
        registryMetrics.count(EVENT.SCHEMA_UPDATE_FAILURE);
        log.info("Registry update failed in {}ms", sw.stop().elapsed(TimeUnit.MILLISECONDS));

        if (!pgConfigurationProperties.isPersistentRegistry()) {
          // while starting with a stale registry might be ok, we cannot start with a non-existant
          // (empty) registry.
          throw new InitialRegistryFetchFailed("While fetching initial state of registry", e);
        }
      }
    }
  }

  @Override
  public void refresh() {
    synchronized (mutex) {
      registryMetrics.timed(
          OP.REFRESH_REGISTRY,
          () -> {
            try {
              Optional<RegistryIndex> fetchIndex = indexFetcher.fetchIndex();
              fetchIndex.ifPresent(this::process);
            } catch (Throwable e) {
              registryMetrics.count(EVENT.SCHEMA_UPDATE_FAILURE);
              throw e;
            }
          });
    }
  }

  protected void process(RegistryIndex index) {
    updateSchemes(index);
    updateTransformations(index);
  }

  private void updateSchemes(RegistryIndex index) {
    List<SchemaSource> toFetch = index.schemes();
    if (!toFetch.isEmpty()) {
      int count = toFetch.size();
      log.info(
          "SchemaStore will be updated, {} {} to fetch.", count, count == 1 ? "schema" : "schemes");
      toFetch
          .parallelStream()
          .forEach(
              source -> {
                try {
                  String schema = registryFileFetcher.fetchSchema(source);
                  try {
                    if (!schemaStore.contains(source)) {
                      schemaStore.register(source, schema);
                    }
                  } catch (SchemaConflictException e) {
                    if (pgConfigurationProperties.isAllowSchemaReplace()) {
                      schemaStore.register(source, schema);
                    } else {
                      throw e;
                    }
                  }
                } catch (IOException e) {
                  throw new SchemaRegistryUnavailableException(e);
                }
              });
    }
  }

  private void updateTransformations(RegistryIndex index) {
    List<TransformationSource> toFetch = index.transformations();

    if (!toFetch.isEmpty()) {
      int count = toFetch.size();
      log.info(
          "TransformationStore will be updated, {} {} to fetch.",
          count,
          count == 1 ? "transformation" : "transformations");
      toFetch
          .parallelStream()
          .forEach(
              source -> {
                try {
                  String transformationCode = null;
                  if (!source.isSynthetic()) {
                    transformationCode = registryFileFetcher.fetchTransformation(source);
                  }
                  try {
                    if (!transformationStore.contains(source)) {
                      transformationStore.store(source, transformationCode);
                    }
                  } catch (TransformationConflictException conflict) {
                    if (pgConfigurationProperties.isAllowSchemaReplace()) {
                      transformationStore.store(source, transformationCode);
                    } else {
                      throw conflict;
                    }
                  }

                } catch (IOException e) {
                  throw new SchemaRegistryUnavailableException(e);
                }
              });
    }
  }

  @Override
  public Optional<JsonSchema> get(@NonNull SchemaKey key) {
    return Optional.ofNullable(cache.getIfPresent(key));
  }

  @Override
  public List<Transformation> get(@NonNull TransformationKey key) {
    return transformationStore.get(key);
  }

  @Override
  public void register(@NonNull TransformationStoreListener listener) {
    transformationStore.register(listener);
  }
}
