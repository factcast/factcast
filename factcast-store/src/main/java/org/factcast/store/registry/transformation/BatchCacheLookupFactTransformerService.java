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

import static java.util.Collections.emptyMap;
import static java.util.function.Predicate.not;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import org.factcast.core.Fact;
import org.factcast.core.subscription.TransformationException;
import org.factcast.store.internal.RequestedVersions;
import org.factcast.store.registry.metrics.RegistryMetrics;
import org.factcast.store.registry.transformation.cache.FactWithTargetVersion;
import org.factcast.store.registry.transformation.cache.TransformationCache;
import org.factcast.store.registry.transformation.chains.TransformationChains;
import org.factcast.store.registry.transformation.chains.Transformer;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * This transformer service is initialised with a list of facts for which {@link
 * #transformIfNecessary(Fact, int)} will be called later.
 *
 * <p>Like this, the service can identify the facts that require transformation upfront, query the
 * cache for them and transform the remaining ones not found in the cache.
 *
 * <p>This approach was chosen to allow the subscription to already start sending facts to the
 * client in the case those at the beginning of the list do not require transformation. Then the
 * processing time of the client can be used to query the cache and do the transformations
 * meanwhile.
 */
@Slf4j
public class BatchCacheLookupFactTransformerService extends AbstractFactTransformer {

  @NonNull private final TransformationCache cache;

  /** Keep all facts identified for transformation, together with the desired target version */
  private final Map<Fact, FactWithTargetVersion> identifiedFactsForTransformation;

  /** Result of the first transformation step: the cache lookup */
  private Future<Map<FactWithTargetVersion, Fact>> cacheLookup;

  /**
   * Result of the second transformation step: if wasn't found in the cache, perform actual
   * transformation.
   *
   * <p>This map will be filled with the corresponding futures after the lookup in the cache was
   * done, but before the cacheLookup future returns.
   */
  private final Map<FactWithTargetVersion, CompletableFuture<Fact>> transformedFacts =
      new HashMap<>();

  /**
   * All facts that you plan to pass to {@link #transformIfNecessary(Fact, int)} need to be in
   * factsForCacheWarmup!
   */
  public BatchCacheLookupFactTransformerService(
      @NonNull TransformationChains chains,
      @NonNull Transformer trans,
      @NonNull TransformationCache cache,
      @NonNull RegistryMetrics registryMetrics,
      @NonNull List<Fact> factsForCacheWarmup,
      @NonNull RequestedVersions requestedVersions) {

    super(chains, trans, registryMetrics);

    this.cache = cache;

    if (factsForCacheWarmup.isEmpty()) {
      log.warn("No facts for cache warmup given, this looks like a bug.");
      // in this case, the cacheLookup future, as well as the transformedFacts future, will not be
      // used, as every fact is considered to be in the desired version already.
      this.identifiedFactsForTransformation = emptyMap();

    } else {
      this.identifiedFactsForTransformation =
          identifyAndScheduleFactsForTransformation(factsForCacheWarmup, requestedVersions);
    }
  }

  @NonNull
  private Map<Fact, FactWithTargetVersion> identifyAndScheduleFactsForTransformation(
      @NonNull List<Fact> factsForCacheWarmup, @NonNull RequestedVersions requestedVersions) {

    var identifiedFacts = identifyFactsForTransformation(factsForCacheWarmup, requestedVersions);

    // First step: attempt loading the identified facts from the cache.
    // Not handling exceptions here, as they will be thrown when the subscription requests a fact
    // that required transformation.
    this.cacheLookup =
        CompletableFuture.supplyAsync(() -> cache.find(identifiedFacts.values()))
            // then schedule transformation for all not found in the cache, unless there was a
            // problem loading them from the cache
            .thenApply(factsFromCache -> handleCacheResult(factsFromCache, identifiedFacts));

    return identifiedFacts;
  }

  private Map<Fact, FactWithTargetVersion> identifyFactsForTransformation(
      @NonNull List<Fact> factsForCacheWarmup, @NonNull RequestedVersions requestedVersions) {

    Map<Fact, FactWithTargetVersion> result = new HashMap<>();

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

      result.put(fact, factWithTargetVersion);
    }

    return result;
  }

  /**
   * Schedule the facts for transformation that were identified for transformation but not found in
   * the cache.
   */
  private Map<FactWithTargetVersion, Fact> handleCacheResult(
      Map<FactWithTargetVersion, Fact> fromCache,
      Map<Fact, FactWithTargetVersion> identifiedFacts) {

    var transformationFutures = transformMissingAsync(fromCache, identifiedFacts);
    insertTransformedIntoCacheAsync(transformationFutures);

    // make available for transformIfNecessary...
    this.transformedFacts.putAll(transformationFutures);

    // ... which would not ask for transformed facts before this future returns with the results
    // from the cache
    return fromCache;
  }

  private HashMap<FactWithTargetVersion, CompletableFuture<Fact>> transformMissingAsync(
      Map<FactWithTargetVersion, Fact> fromCache,
      Map<Fact, FactWithTargetVersion> identifiedFacts) {

    var result = new HashMap<FactWithTargetVersion, CompletableFuture<Fact>>();

    identifiedFacts.values().stream()
        .filter(not(fromCache::containsKey))
        // make sure we transform the facts in the order they are requested
        .sorted()
        .forEachOrdered(
            notInCache ->
                result.put(
                    notInCache,
                    CompletableFuture.supplyAsync(
                        () ->
                            transform(
                                notInCache.fact(),
                                notInCache.targetVersion(),
                                notInCache.transformationKey(),
                                notInCache.transformationChain()))));

    return result;
  }

  private void insertTransformedIntoCacheAsync(
      HashMap<FactWithTargetVersion, CompletableFuture<Fact>> transformationFutures) {

    if (transformationFutures.isEmpty()) {
      return;
    }

    var transformedAsArray = transformationFutures.values().toArray(CompletableFuture[]::new);

    CompletableFuture
        // wait until all transformations are done
        .allOf(transformedAsArray)
        // ignore transformation errors. The first transformation error will be logged when (and if)
        // the subscription asks for that fact. If another exception happens before that, the
        // transformation error will be lost, but it is ok, as we would otherwise flood the log with
        // many errors. Let's better only log the first error that occurred instead.
        .exceptionally(e -> null)
        .thenRun(() -> updateCacheAfterAllTransformationsAreDone(transformationFutures))
        .whenComplete(
            (result, ex) -> {
              if (ex != null) {
                // ex is != null only if we had an error updating the transformation cache, which we
                // should log
                log.error("Error updating transformation cache.", ex);
              } else {
                log.debug("Finished updating transformation cache.");
              }
            });
  }

  private void updateCacheAfterAllTransformationsAreDone(
      HashMap<FactWithTargetVersion, CompletableFuture<Fact>> transformationFutures) {

    var transformedFactsWithTargetVersion =
        transformationFutures.entrySet().stream()
            // only update facts that were transformed successfully
            .filter(e -> !e.getValue().isCompletedExceptionally() && !e.getValue().isCancelled())
            // after we filtered, getNow should not throw an exception.
            .map(e -> e.getKey().replaceFactWith(e.getValue().getNow(null)))
            // It should also not return null, as we synchronized earlier, but let's filter nulls
            // out to be on the safe side
            .filter(f -> f.fact() != null)
            .collect(Collectors.toSet());

    cache.put(transformedFactsWithTargetVersion);
  }

  /**
   * This method transforms the given fact if required, either by finding it in the cache or
   * transforming it.
   *
   * <p>Note: all facts requested here must have been passed to the constructor, otherwise they will
   * be returned as they are, even with a wrong version.
   */
  @Override
  public Fact transformIfNecessary(Fact e, int targetVersion) throws TransformationException {

    var identifiedFactWithTargetVersion = identifiedFactsForTransformation.get(e);

    if (identifiedFactWithTargetVersion == null) {
      // this fact was not identified for transformation,
      // which means the fact is already in a desired version
      return e;
    }

    try {
      // first check if was found in the cache.
      // this will throw an exception in case there was an error loading from the cache
      // or *scheduling* the transformation for the ones not found in the cache
      var fromCache = cacheLookup.get().get(identifiedFactWithTargetVersion);
      if (fromCache != null) {
        return fromCache;
      }

      // if it was not in the cache, it was scheduled for transformation.
      // this will throw an exception in case the transformation itself failed.
      var transformed = transformedFacts.get(identifiedFactWithTargetVersion).get();
      if (transformed == null) {
        throw new IllegalStateException(
            "Fact which required transformation was neither found in cache nor in transformation map. This is a bug.");
      }

      return transformed;

    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new TransformationException(exception);

    } catch (ExecutionException exception) {
      throw new TransformationException(exception);
    }
  }
}
