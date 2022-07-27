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

import java.util.*;
import java.util.stream.*;

import org.factcast.core.Fact;
import org.factcast.core.subscription.TransformationException;
import org.factcast.core.subscription.transformation.FactTransformerService;
import org.factcast.core.subscription.transformation.TransformationRequest;
import org.factcast.core.util.FactCastJson;
import org.factcast.store.internal.Pair;
import org.factcast.store.registry.metrics.RegistryMetrics;
import org.factcast.store.registry.transformation.cache.TransformationCache;
import org.factcast.store.registry.transformation.chains.TransformationChain;
import org.factcast.store.registry.transformation.chains.TransformationChains;
import org.factcast.store.registry.transformation.chains.Transformer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.annotations.VisibleForTesting;

import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class FactTransformerServiceImpl implements FactTransformerService {

  @NonNull private final TransformationChains chains;

  @NonNull private final Transformer trans;

  @NonNull private final TransformationCache cache;

  @NonNull private final RegistryMetrics registryMetrics;

  @Override
  public Fact transform(@NonNull TransformationRequest req) throws TransformationException {
    Fact e = req.toTransform();
    Set<Integer> targetVersions = req.targetVersions();
    int sourceVersion = e.version();
    if (targetVersions.contains(sourceVersion) || targetVersions.contains(0)) {
      return e;
    }

    TransformationKey key = TransformationKey.of(e.ns(), e.type());
    TransformationChain chain = chains.get(key, sourceVersion, req.targetVersions());
    String chainId = chain.id();
    TransformationCache.Key cacheKey =
        TransformationCache.Key.of(e.id(), chain.toVersion(), chainId);
    return cache.find(cacheKey).orElseGet(() -> doTransform(e, chain));
  }

  @Override
  public List<Fact> transform(@NonNull List<TransformationRequest> req)
      throws TransformationException {

    log.trace("batch processing  " + req.size() + " transformation requests");

    List<Pair<TransformationRequest, TransformationChain>> pairs =
        req.stream().map(r -> Pair.of(r, toChain(r))).collect(Collectors.toList());
    Set<TransformationCache.Key> keys =
        pairs.parallelStream()
            .map(
                p ->
                    TransformationCache.Key.of(
                        p.left().toTransform().id(), p.right().toVersion(), p.right().id()))
            .collect(Collectors.toSet());

    Map<UUID, Fact> found =
        cache.findAll(keys).stream().collect(Collectors.toMap(Fact::id, f -> f));
    log.trace("batch lookup found {} out of {} pre transformed facts", found.size(), req.size());

    Stream<Pair<TransformationRequest, TransformationChain>> pairStream = pairs.stream();
    if (shouldBeParallel(pairs.stream().map(Pair::right))) pairStream = pairStream.parallel();
    return pairStream
        .map(
            c -> {
              Fact e = c.left().toTransform();
              Fact cached = found.get(e.id());
              if (cached != null) return cached;
              else {
                return doTransform(e, c.right());
              }
            })
        .collect(Collectors.toList());
  }

  /**
   * idea is to prevent the overhead of going parallel if all chains target the same warm
   * script-engine as we'd have a serializing effect there due to synchronization.
   *
   * @return if the transformations should go parallel
   */
  @VisibleForTesting
  boolean shouldBeParallel(@NonNull Stream<TransformationChain> chains) {
    // if there is more than one distinct engine used, return true
    return chains
            .filter(Objects::nonNull)
            .mapToInt(tc -> tc.id().hashCode() + tc.key().hashCode())
            .distinct()
            .count()
        > 1;
  }

  @NonNull
  public Fact doTransform(@NonNull Fact e, @NonNull TransformationChain chain) {

    return registryMetrics.timed(
        RegistryMetrics.OP.TRANSFORMATION,
        () -> {
          try {
            JsonNode input = FactCastJson.readTree(e.jsonPayload());
            JsonNode header = FactCastJson.readTree(e.jsonHeader());
            ((ObjectNode) header).put("version", chain.toVersion());
            JsonNode transformedPayload = trans.transform(chain, input);
            Fact transformed = Fact.of(header, transformedPayload);
            cache.put(
                TransformationCache.Key.of(transformed.id(), transformed.version(), chain.id()),
                transformed);
            return transformed;
          } catch (Exception e1) {
            registryMetrics.count(
                RegistryMetrics.EVENT.TRANSFORMATION_FAILED,
                Tags.of(
                    Tag.of(RegistryMetrics.TAG_IDENTITY_KEY, String.valueOf(chain.key())),
                    Tag.of("version", String.valueOf(chain.toVersion()))));
            throw new TransformationException(e1);
          }
        });
  }

  private TransformationChain toChain(TransformationRequest req) {
    @NonNull Fact e = req.toTransform();
    int sourceVersion = e.version();
    TransformationKey key = TransformationKey.of(e.ns(), e.type());
    return chains.get(key, sourceVersion, req.targetVersions());
  }
}
