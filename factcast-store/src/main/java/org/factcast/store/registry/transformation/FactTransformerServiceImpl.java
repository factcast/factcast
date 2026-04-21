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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.Fact;
import org.factcast.core.subscription.TransformationException;
import org.factcast.core.util.ExceptionHelper;
import org.factcast.core.util.FactCastJson;
import org.factcast.store.StoreConfigurationProperties;
import org.factcast.store.internal.Pair;
import org.factcast.store.internal.PgFact;
import org.factcast.store.internal.script.JsonString;
import org.factcast.store.internal.transformation.FactTransformerService;
import org.factcast.store.internal.transformation.TransformationRequest;
import org.factcast.store.registry.metrics.RegistryMetrics;
import org.factcast.store.registry.transformation.cache.TransformationCache;
import org.factcast.store.registry.transformation.chains.TransformationChain;
import org.factcast.store.registry.transformation.chains.TransformationChains;
import org.factcast.store.registry.transformation.chains.Transformer;
import org.slf4j.MDC;

@Slf4j
public class FactTransformerServiceImpl implements FactTransformerService, AutoCloseable {

  @NonNull private final TransformationChains chains;

  @NonNull private final Transformer trans;

  @NonNull private final TransformationCache cache;

  @NonNull private final RegistryMetrics registryMetrics;

  private final ExecutorService pool;

  public FactTransformerServiceImpl(
      @NonNull TransformationChains chains,
      @NonNull Transformer trans,
      @NonNull TransformationCache cache,
      @NonNull RegistryMetrics registryMetrics,
      @NonNull StoreConfigurationProperties props) {
    this.chains = chains;
    this.trans = trans;
    this.cache = cache;
    this.registryMetrics = registryMetrics;
    this.pool =
        registryMetrics.monitor(
            new ForkJoinPool(props.getSizeOfThreadPoolForBufferedTransformations()),
            "parallel-transformation");
  }

  @Override
  public Fact transform(@NonNull TransformationRequest req) throws TransformationException {
    PgFact e = req.toTransform();
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
  public List<PgFact> transform(@NonNull List<TransformationRequest> req)
      throws TransformationException {

    if (req.isEmpty()) {
      return Collections.emptyList();
    }

    try {
      // Capture caller's MDC so it propagates to pool threads.
      // This propagation can be rolled back if pool threads turn out not to emit
      // problematic TRACE logs — currently only 2 summary-level trace calls here.
      Map<String, String> callerMdc = MDC.getCopyOfContextMap();

      return CompletableFuture.supplyAsync(
              () -> {
                setMdc(callerMdc);
                try {
                  log.trace("batch processing {} transformation requests", req.size());

                  List<Pair<TransformationRequest, TransformationChain>> pairs =
                      req.stream().map(r -> Pair.of(r, toChain(r))).toList();

                  Set<TransformationCache.Key> keys =
                      pairs.parallelStream()
                          .map(
                              p ->
                                  TransformationCache.Key.of(
                                      p.left().toTransform().id(),
                                      p.right().toVersion(),
                                      p.right().id()))
                          .collect(Collectors.toSet());

                  // ConcurrentHashMap needed because remove is used from a potentially
                  // parallel stream below
                  Map<UUID, PgFact> found =
                      cache.findAll(keys).stream()
                          .collect(Collectors.toConcurrentMap(PgFact::id, f -> f));
                  log.trace(
                      "batch lookup found {} out of {} pre transformed facts",
                      found.size(),
                      req.size());

                  Stream<Pair<TransformationRequest, TransformationChain>> pairStream =
                      pairs.parallelStream();

                  try {

                    // trying to avoid default FJP
                    // https://blog.krecan.net/2014/03/18/how-to-specify-thread-pool-for-java-8-parallel-streams/

                    return pool.submit(
                            () ->
                                pairStream
                                    .map(
                                        c -> {
                                          PgFact e = c.left().pop();
                                          PgFact cached = found.remove(e.id());
                                          return Objects.requireNonNullElseGet(
                                              cached, () -> doTransform(e, c.right()));
                                        })
                                    .toList())
                        .get();
                  } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw ExceptionHelper.toRuntime(e);
                  } catch (Exception e) {
                    throw ExceptionHelper.toRuntime(e.getCause());
                  }
                } finally {
                  MDC.clear();
                }
              },
              pool)
          .get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw ExceptionHelper.toRuntime(e);
    } catch (ExecutionException e) {
      // make sure TransformationExceptions are escalated as such
      throw ExceptionHelper.toRuntime(e.getCause());
    }
  }

  @NonNull
  public PgFact doTransform(@NonNull PgFact e, @NonNull TransformationChain chain) {

    return registryMetrics.timed(
        RegistryMetrics.OP.TRANSFORMATION,
        () -> {
          try {

            // patch new version to header
            JsonNode header = FactCastJson.readTree(e.jsonHeader());
            ((ObjectNode) header).put("version", chain.toVersion());

            JsonString input = JsonString.of(e.jsonPayload());
            JsonString transformedPayload = trans.transform(chain, input);

            // we're intentionally using string here, keeping the jsonNodes around consumes more
            // memory
            PgFact transformed = PgFact.of(header.toString(), transformedPayload.json());
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
            throw new TransformationException("Failed to transform " + chain, e1);
          }
        });
  }

  private static void setMdc(Map<String, String> mdc) {
    if (mdc != null) {
      MDC.setContextMap(mdc);
    } else {
      MDC.clear();
    }
  }

  private TransformationChain toChain(TransformationRequest req) {
    @NonNull Fact e = req.toTransform();
    int sourceVersion = e.version();
    TransformationKey key = TransformationKey.of(e.ns(), e.type());
    return chains.get(key, sourceVersion, req.targetVersions());
  }

  @Override
  public void close() throws Exception {
    pool.shutdownNow();
  }
}
