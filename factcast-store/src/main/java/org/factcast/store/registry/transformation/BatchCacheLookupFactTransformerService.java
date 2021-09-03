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

import java.util.List;
import java.util.Optional;

import org.factcast.core.Fact;
import org.factcast.core.subscription.TransformationException;
import org.factcast.store.registry.metrics.RegistryMetrics;
import org.factcast.store.registry.transformation.cache.TransformationCache;
import org.factcast.store.registry.transformation.chains.TransformationChain;
import org.factcast.store.registry.transformation.chains.TransformationChains;
import org.factcast.store.registry.transformation.chains.Transformer;

import lombok.NonNull;

public class BatchCacheLookupFactTransformerService extends AbstractFactTransformer {

  @NonNull private final TransformationCache cache;

  public BatchCacheLookupFactTransformerService(
      @NonNull TransformationChains chains,
      @NonNull Transformer trans,
      @NonNull TransformationCache cache,
      @NonNull RegistryMetrics registryMetrics,
      @NonNull List<Fact> factsForCacheWarmup) {
    super(chains, trans, registryMetrics);
    this.cache = cache;
    warmupCache(factsForCacheWarmup);
  }

  private void warmupCache(List<Fact> factsForCacheWarmup) {
    // TODO implement me
  }

  @Override
  public Fact transformIfNecessary(Fact e, int targetVersion) throws TransformationException {

    if (!isTransformationNecessary(e, targetVersion)) {
      return e;
    }

    TransformationKey key = TransformationKey.of(e.ns(), e.type());
    TransformationChain chain = getChain(e, targetVersion, key);

    // TODO: instead use warmed-up cache
    Optional<Fact> cached = cache.find(e.id(), targetVersion, chain.id());

    if (cached.isPresent()) {
      return cached.get();

    } else {
      // TODO: instead use transformation futures
      Fact transformed = transform(e, targetVersion, key, chain);
      // TODO: remove this
      cache.put(transformed, chain.id());
      return transformed;
    }
  }
}
