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
package org.factcast.store.registry;

import com.google.common.base.Stopwatch;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.everit.json.schema.Schema;
import org.factcast.store.StoreConfigurationProperties;
import org.factcast.store.registry.http.ValidationConstants;
import org.factcast.store.registry.metrics.RegistryMetrics;
import org.factcast.store.registry.transformation.Transformation;
import org.factcast.store.registry.transformation.TransformationConflictException;
import org.factcast.store.registry.transformation.TransformationKey;
import org.factcast.store.registry.transformation.TransformationSource;
import org.factcast.store.registry.transformation.TransformationStore;
import org.factcast.store.registry.transformation.TransformationStoreListener;
import org.factcast.store.registry.validation.schema.SchemaConflictException;
import org.factcast.store.registry.validation.schema.SchemaKey;
import org.factcast.store.registry.validation.schema.SchemaSource;
import org.factcast.store.registry.validation.schema.SchemaStore;

@Slf4j
public abstract class AbstractSchemaRegistry implements SchemaRegistry {
  @NonNull protected final IndexFetcher indexFetcher;

  @NonNull protected final RegistryFileFetcher registryFileFetcher;

  @NonNull protected final SchemaStore schemaStore;

  @NonNull protected final TransformationStore transformationStore;

  @NonNull protected final RegistryMetrics registryMetrics;
  @NonNull protected final StoreConfigurationProperties storeConfigurationProperties;
  @NonNull protected final LockProvider lockProvider;

  protected final Object mutex = new Object();

  final CacheLoader<SchemaKey, Optional<Schema>> schemaLoader =
      new CacheLoader<>() {

        // optional is necessary because null as a return from load is not allowed by API
        @Override
        public @NonNull Optional<Schema> load(@NonNull SchemaKey key) {
          return schemaStore.get(key).map(ValidationConstants.jsonString2SchemaV7());
        }
      };

  private final LoadingCache<SchemaKey, Optional<Schema>> schemaNearCache =
      CacheBuilder.newBuilder().maximumSize(10000).build(schemaLoader);

  public AbstractSchemaRegistry(
      @NonNull IndexFetcher indexFetcher,
      @NonNull RegistryFileFetcher registryFileFetcher,
      @NonNull SchemaStore schemaStore,
      @NonNull TransformationStore transformationStore,
      @NonNull RegistryMetrics registryMetrics,
      @NonNull StoreConfigurationProperties storeConfigurationProperties,
      @NonNull LockProvider lockProvider) {
    this.indexFetcher = indexFetcher;
    this.registryFileFetcher = registryFileFetcher;
    this.schemaStore = schemaStore;
    this.transformationStore = transformationStore;
    this.registryMetrics = registryMetrics;
    this.storeConfigurationProperties = storeConfigurationProperties;
    this.lockProvider = lockProvider;
  }

  @Override
  public void fetchInitial() {
    if (storeConfigurationProperties.isPersistentRegistry()) {
      log.info("Acquiring lock for registry update");
      LockConfiguration lockConfig =
          new LockConfiguration(
              SchemaRegistry.LOCK_NAME, Duration.ofMinutes(1), Duration.ofSeconds(2));
      Optional<SimpleLock> lock = lockProvider.lock(lockConfig);
      if (lock.isPresent()) {
        try {
          doFetchInitial();
        } finally {
          lock.get().unlock();
        }
      } else {
        log.warn("lock already exists, skipping initial schema-registry update");
      }
    } else doFetchInitial();
  }

  private void doFetchInitial() {
    synchronized (mutex) {
      Stopwatch sw = Stopwatch.createStarted();
      log.info("Registry update started");
      try {
        indexFetcher.fetchIndex().ifPresent(this::process);
        log.info("Registry update finished in {}ms", sw.stop().elapsed(TimeUnit.MILLISECONDS));
      } catch (Throwable e) {
        log.warn("While fetching initial state of registry", e);
        registryMetrics.count(RegistryMetrics.EVENT.SCHEMA_UPDATE_FAILURE);
        log.info("Registry update failed in {}ms", sw.stop().elapsed(TimeUnit.MILLISECONDS));

        if (!storeConfigurationProperties.isPersistentRegistry()) {
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
          RegistryMetrics.OP.REFRESH_REGISTRY,
          () -> {
            try {
              Optional<RegistryIndex> fetchIndex = indexFetcher.fetchIndex();
              fetchIndex.ifPresent(this::process);
            } catch (Throwable e) {
              registryMetrics.count(RegistryMetrics.EVENT.SCHEMA_UPDATE_FAILURE);
              throw e;
            }
          });
    }
  }

  protected void process(RegistryIndex index) {
    updateSchemes(index);
    updateTransformations(index);
    // clearNearCache();
  }

  public void clearNearCache() {
    schemaNearCache.invalidateAll();
  }

  public void invalidateNearCache(SchemaKey key) {
    schemaNearCache.invalidate(key);
  }

  private void updateSchemes(RegistryIndex index) {
    List<SchemaSource> toFetch = index.schemes();
    if (!toFetch.isEmpty()) {
      int count = toFetch.size();
      log.info(
          "SchemaStore will be updated, {} {} to fetch.", count, count == 1 ? "schema" : "schemes");
      toFetch.stream()
          .forEach(
              source -> {
                try {
                  String schema = registryFileFetcher.fetchSchema(source);
                  try {
                    if (!schemaStore.contains(source)) {
                      schemaStore.register(source, schema);
                    }
                  } catch (SchemaConflictException e) {
                    if (storeConfigurationProperties.isAllowSchemaReplace()) {
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
      toFetch.stream()
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
                    if (storeConfigurationProperties.isAllowSchemaReplace()) {
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

  @SneakyThrows
  @Override
  public Optional<Schema> get(@NonNull SchemaKey key) {
    return schemaNearCache.get(key);
  }

  @Override
  public List<Transformation> get(@NonNull TransformationKey key) {
    return transformationStore.get(key);
  }

  @Override
  public void register(@NonNull TransformationStoreListener listener) {
    transformationStore.register(listener);
  }

  /**
   * This method queries the schema store directly for all the SchemaKeys and doesn't use the
   * schemaNearCache
   *
   * @return the set of all namespaces
   */
  @Override
  public Set<String> enumerateNamespaces() {
    return schemaStore.getAllSchemaKeys().stream().map(SchemaKey::ns).collect(Collectors.toSet());
  }

  /**
   * This method queries the schema store directly for all the SchemaKeys and doesn't use the
   * schemaNearCache
   *
   * @return the set of all types
   */
  @Override
  public Set<String> enumerateTypes() {
    return schemaStore.getAllSchemaKeys().stream().map(SchemaKey::type).collect(Collectors.toSet());
  }
}
