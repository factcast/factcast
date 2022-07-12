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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import java.util.*;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.factcast.core.Fact;
import org.factcast.core.subscription.FactTransformerService;
import org.factcast.core.subscription.TransformationException;
import org.factcast.core.util.FactCastJson;
import org.factcast.store.registry.metrics.RegistryMetrics;
import org.factcast.store.registry.transformation.cache.TransformationCache;
import org.factcast.store.registry.transformation.chains.TransformationChain;
import org.factcast.store.registry.transformation.chains.TransformationChains;
import org.factcast.store.registry.transformation.chains.Transformer;

@RequiredArgsConstructor
public class FactTransformerServiceImpl implements FactTransformerService {

  @NonNull private final TransformationChains chains;

  @NonNull private final Transformer trans;

  @NonNull private final TransformationCache cache;

  @NonNull private final RegistryMetrics registryMetrics;

  @Override
  public Fact transformIfNecessary(Fact e, Set<Integer> targetVersions)
      throws TransformationException {

    int sourceVersion = e.version();
    if (targetVersions.contains(sourceVersion) || targetVersions.contains(0)) {
      return e;
    }

    TransformationKey key = TransformationKey.of(e.ns(), e.type());
    TransformationChain chain = chains.get(key, sourceVersion, targetVersions);

    String chainId = chain.id();
    // find does not need the target version param
    Optional<Fact> cached = cache.find(e.id(), chain.toVersion(), chainId);
    if (cached.isPresent()) {
      return cached.get();
    } else {
      try {
        JsonNode input = FactCastJson.readTree(e.jsonPayload());
        JsonNode header = FactCastJson.readTree(e.jsonHeader());
        ((ObjectNode) header).put("version", chain.toVersion());
        JsonNode transformedPayload = trans.transform(chain, input);
        Fact transformed = Fact.of(header, transformedPayload);
        // can be optimized by passing jsonnode?
        cache.put(transformed, chainId);
        return transformed;
      } catch (JsonProcessingException e1) {
        registryMetrics.count(
            RegistryMetrics.EVENT.TRANSFORMATION_FAILED,
            Tags.of(
                Tag.of(RegistryMetrics.TAG_IDENTITY_KEY, key.toString()),
                Tag.of("version", String.valueOf(targetVersions))));

        throw new TransformationException(e1);
      }
    }
  }
}
