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
package org.factcast.store.registry.transformation;

import static java.util.function.Predicate.not;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.factcast.core.Fact;
import org.factcast.core.subscription.TransformationException;
import org.factcast.store.internal.RequestedVersions;
import org.factcast.store.registry.metrics.RegistryMetrics;
import org.factcast.store.registry.transformation.cache.FactWithTargetVersion;
import org.factcast.store.registry.transformation.cache.TransformationCache;
import org.factcast.store.registry.transformation.chains.TransformationChains;
import org.factcast.store.registry.transformation.chains.Transformer;

import lombok.NonNull;

public class BatchCacheLookupFactTransformerService extends AbstractFactTransformer {

  @NonNull private final TransformationCache cache;

  /**
   * Contains the original facts with corresponding target versions for those facts that needs
   * transformation.
   */
  private final Map<Fact, FactWithTargetVersion> factToTargetVersions = new HashMap<>();

  private Future<Map<FactWithTargetVersion, Fact>> cacheLookup;
  private Map<FactWithTargetVersion, Future<Fact>> transformedFacts = new HashMap<>();

  public BatchCacheLookupFactTransformerService(
      @NonNull TransformationChains chains,
      @NonNull Transformer trans,
      @NonNull TransformationCache cache,
      @NonNull RegistryMetrics registryMetrics,
      @NonNull List<Fact> factsForCacheWarmup,
      @NonNull RequestedVersions requestedVersions) {
    super(chains, trans, registryMetrics);
    this.cache = cache;
    warmupCache(factsForCacheWarmup, requestedVersions);
  }

  private void warmupCache(
      @NonNull List<Fact> factsForCacheWarmup, @NonNull RequestedVersions requestedVersions) {

    for (int i = 0; i < factsForCacheWarmup.size(); i++) {

      Fact fact = factsForCacheWarmup.get(i);

      if (requestedVersions.noTypeOrMatches(fact)) {
        continue;
      }

      var key = TransformationKey.of(fact.ns(), fact.type());
      var targetVersion = requestedVersions.getTargetVersion(fact);

      if (!isTransformationNecessary(fact, targetVersion)) {
        continue;
      }

      var chain = getChain(fact, targetVersion, key);

      var factWithTargetVersion = new FactWithTargetVersion(i, fact, targetVersion, key, chain);

      factToTargetVersions.put(fact, factWithTargetVersion);
    }

    this.cacheLookup =
        CompletableFuture.supplyAsync(() -> cache.find(factToTargetVersions.values()))
            .thenApply(this::handleCacheResult);
  }

  private Map<FactWithTargetVersion, Fact> handleCacheResult(
      Map<FactWithTargetVersion, Fact> fromCache) {

    // TODO: update read timestamps from cache

    factToTargetVersions.values().stream()
        .filter(not(fromCache::containsKey))
        // make sure we transform the facts in the order they are requested
        .sorted()
        .forEachOrdered(
            notInCache ->
                transformedFacts.put(
                    notInCache,
                    CompletableFuture.supplyAsync(
                        () ->
                            // TODO: insert into cache
                            transform(
                                notInCache.fact(),
                                notInCache.targetVersion(),
                                notInCache.transformationKey(),
                                notInCache.transformationChain()))));

    return fromCache;
  }

  @Override
  public Fact transformIfNecessary(Fact e, int targetVersion) throws TransformationException {

    var factWithTargetVersion = factToTargetVersions.get(e);

    if (factWithTargetVersion == null) {
      return e;
    }

    try {
      var fromCache = cacheLookup.get().get(factWithTargetVersion);
      if (fromCache != null) {
        return fromCache;
      }

      var transformed = transformedFacts.get(factWithTargetVersion).get();
      if (transformed == null) {
        throw new IllegalStateException(
            "Fact which required transformation was neither found in cache nor in transformation map. This is a bug.");
      }

      return transformed;

    } catch (InterruptedException | ExecutionException exception) {
      throw new TransformationException(exception);
    }
  }
}
