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
package org.factcast.store.registry.transformation.chains;

import java.util.*;
import java.util.function.*;
import java.util.stream.*;

import org.factcast.core.subscription.transformation.MissingTransformationInformationException;
import org.factcast.store.registry.SchemaRegistry;
import org.factcast.store.registry.metrics.RegistryMetrics;
import org.factcast.store.registry.transformation.Transformation;
import org.factcast.store.registry.transformation.TransformationKey;
import org.factcast.store.registry.transformation.TransformationStoreListener;

import com.google.common.collect.Iterables;

import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;

import es.usc.citius.hipster.algorithm.AStar;
import es.usc.citius.hipster.algorithm.Algorithm;
import es.usc.citius.hipster.algorithm.Hipster;
import es.usc.citius.hipster.graph.GraphBuilder;
import es.usc.citius.hipster.graph.GraphSearchProblem;
import es.usc.citius.hipster.graph.HipsterDirectedGraph;
import es.usc.citius.hipster.model.impl.WeightedNode;
import lombok.Value;

public class TransformationChains implements TransformationStoreListener {

  private static final double BASE_COST = 1_000_000d;

  private final SchemaRegistry registry;

  private final RegistryMetrics registryMetrics;

  private final Map<TransformationKey, Map<VersionPath, TransformationChain>> cache =
      new HashMap<>();

  @Value
  static class VersionPath {
    int fromVersion;

    int toVersion;
  }

  public TransformationChains(SchemaRegistry r, RegistryMetrics registryMetrics) {
    registry = r;
    this.registryMetrics = registryMetrics;
    r.register(this);
  }

  public TransformationChain get(TransformationKey key, int from, int to)
      throws MissingTransformationInformationException {

    Map<VersionPath, TransformationChain> chainsPerKey;

    synchronized (cache) {
      // sync is necessary, because we don't want to end up with two
      // different maps we're locking the whole cache, but creating a
      // hashmap should not take too much time
      chainsPerKey = cache.computeIfAbsent(key, k -> new HashMap<>());
    }
    synchronized (chainsPerKey) {
      // we're locking the map for this particular key. contention should
      // be limited and the gain of not creating unnecessary chains should
      // be on the plus side.
      return chainsPerKey.computeIfAbsent(new VersionPath(from, to), p -> build(key, from, to));
    }
  }

  private TransformationChain build(TransformationKey key, int from, int to)
      throws MissingTransformationInformationException {

    GraphBuilder<Integer, Edge> builder = GraphBuilder.create();
    List<Transformation> all = registry.get(key);
    if (all.isEmpty()) {
      registryMetrics.count(
          RegistryMetrics.EVENT.MISSING_TRANSFORMATION_INFO,
          Tags.of(
              Tag.of(RegistryMetrics.TAG_IDENTITY_KEY, key.toString()),
              Tag.of("from", String.valueOf(from)),
              Tag.of("to", String.valueOf(to))));

      throw new MissingTransformationInformationException("No Transformations for " + key);
    }

    // populate graph
    for (Transformation t : all) {
      builder.connect(t.fromVersion()).to(t.toVersion()).withEdge(Edge.from(t));
    }
    HipsterDirectedGraph<Integer, Edge> g = builder.createDirectedGraph();

    // create problem
    AStar<Edge, Integer, Double, WeightedNode<Edge, Integer, Double>> problem =
        Hipster.createDijkstra(
            GraphSearchProblem.startingFrom(from)
                .in(g)
                .extractCostFromEdges(
                    e -> e.fromVersion() * (e.toVersion() - e.fromVersion()) + BASE_COST)
                .build());

    // run search
    Algorithm<Edge, Integer, WeightedNode<Edge, Integer, Double>>.SearchResult r =
        problem.search(to);

    WeightedNode<Edge, Integer, Double> goalNode = r.getGoalNode();
    List<Edge> path = Algorithm.recoverActionPath(goalNode);

    if (path.isEmpty()
        || Iterables.getLast(path).toVersion() != to
        || Iterables.getFirst(path, null).fromVersion() != from) {
      registryMetrics.count(
          RegistryMetrics.EVENT.MISSING_TRANSFORMATION_INFO,
          Tags.of(
              Tag.of(RegistryMetrics.TAG_IDENTITY_KEY, key.toString()),
              Tag.of("from", String.valueOf(from)),
              Tag.of("to", String.valueOf(to))));

      throw new MissingTransformationInformationException(
          "Cannot reach version " + to + " from version " + from + " for " + key);
    }
    List<Transformation> steps = map(path, Edge::transformation);
    return TransformationChain.of(key, steps, r.getOptimalPaths().get(0).toString());

    // sad: in retrospective, Hipster might not have been the greatest
    // choice due to lack of proper Generics.
  }

  private static <N, E> List<E> map(List<N> list, Function<N, E> f) {
    return list.stream().map(f).collect(Collectors.toList());
  }

  @Override
  public void notifyFor(TransformationKey key) {
    // invalidate cache for key
    cache.remove(key);
  }
}
