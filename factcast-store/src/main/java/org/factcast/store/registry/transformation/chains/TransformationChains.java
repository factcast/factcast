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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
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
import lombok.NonNull;
import lombok.Value;

public class TransformationChains implements TransformationStoreListener {

  private static final double BASE_COST = 1d;

  private final SchemaRegistry registry;

  private final RegistryMetrics registryMetrics;

  private final Map<TransformationKey, Map<VersionPath, TransformationChain>> cache =
      new HashMap<>();

  @Value
  static class VersionPath {
    int fromVersion;
    @NonNull Set<Integer> toVersions;
  }

  public TransformationChains(SchemaRegistry r, RegistryMetrics registryMetrics) {
    registry = r;
    this.registryMetrics = registryMetrics;
    r.register(this);
  }

  public TransformationChain get(
      @NonNull TransformationKey key, int from, @NonNull Set<Integer> requestedVersions)
      throws MissingTransformationInformationException {

    Preconditions.checkState(!requestedVersions.isEmpty());

    Map<VersionPath, TransformationChain> chainsPerKey;

    synchronized (cache) {
      // sync is necessary, because we don't want to end up with two
      // different maps we're locking the whole cache, but creating a
      // hashmap should not take too much time
      chainsPerKey = cache.computeIfAbsent(key, k -> new HashMap<>());

      // we're locking the map for this particular key. contention should
      // be limited and the gain of not creating unnecessary chains should
      // be on the plus side.
      return chainsPerKey.computeIfAbsent(
          new VersionPath(from, requestedVersions), p -> build(key, from, requestedVersions));
    }
  }

  @VisibleForTesting
  TransformationChain build(@NonNull TransformationKey key, int from, @NonNull Set<Integer> to)
      throws MissingTransformationInformationException {
    Preconditions.checkState(!to.isEmpty());

    HipsterDirectedGraph<Integer, Edge> g = createGraph(key, from, to);
    AStar<Edge, Integer, Double, WeightedNode<Edge, Integer, Double>> problem =
        createProblem(from, g);

    // not sure about using a set here... better safe than sorry
    List<List<Edge>> possiblePaths = new ArrayList<>();
    to.forEach(
        targetVersion -> {
          Algorithm<Edge, Integer, WeightedNode<Edge, Integer, Double>>.SearchResult result =
              problem.search(targetVersion);
          if (result != null) {
            WeightedNode<Edge, Integer, Double> goalNode = result.getGoalNode();
            List<Edge> path = Algorithm.recoverActionPath(goalNode);
            if (!path.isEmpty()
                && to.contains(Iterables.getLast(path).toVersion())
                && Iterables.getFirst(path, null).fromVersion() == from) {
              possiblePaths.add(path);
            }
          }
        });

    if (possiblePaths.isEmpty()) {
      registryMetrics.count(
          RegistryMetrics.EVENT.MISSING_TRANSFORMATION_INFO,
          Tags.of(
              Tag.of(RegistryMetrics.TAG_IDENTITY_KEY, key.toString()),
              Tag.of("from", String.valueOf(from)),
              Tag.of("to", String.valueOf(to))));

      throw new MissingTransformationInformationException(
          "Cannot reach any version in " + to + " from version " + from + " for " + key);
    } else {
      // choose shortest path with bias to later versions
      List<Edge> finalPath = pickFinalPath(possiblePaths);
      List<Transformation> steps = map(finalPath, Edge::transformation);
      return TransformationChain.of(key, steps, toString(finalPath));
    }
  }

  @SuppressWarnings("OptionalGetWithoutIsPresent")
  private String toString(@NonNull List<Edge> finalPath) {
    if (finalPath.isEmpty()) return "[]";
    else
      return "["
          + finalPath.stream().findFirst().map(Edge::fromVersion).get()
          + finalPath.stream().map(Edge::toVersion).map(s -> ", " + s).collect(Collectors.joining())
          + "]";
  }

  @SuppressWarnings("OptionalGetWithoutIsPresent")
  @VisibleForTesting
  List<Edge> pickFinalPath(@NonNull List<List<Edge>> possiblePaths) {
    Preconditions.checkState(!possiblePaths.isEmpty());

    int minSize = possiblePaths.stream().mapToInt(List::size).min().getAsInt();
    return possiblePaths.stream()
        // just take the shortest
        .filter(l -> l.size() == minSize)
        // and pick the one with the highes sum of node versions
        .max(Comparator.comparingInt(a -> a.stream().mapToInt(Edge::toVersion).sum()))
        .get();
  }

  @NonNull
  private AStar<Edge, Integer, Double, WeightedNode<Edge, Integer, Double>> createProblem(
      int from, @NonNull HipsterDirectedGraph<Integer, Edge> directedGraph) {
    return Hipster.createDijkstra(
        GraphSearchProblem.startingFrom(from)
            .in(directedGraph)
            .extractCostFromEdges(e -> BASE_COST)
            .build());
  }

  private HipsterDirectedGraph<Integer, Edge> createGraph(
      @NonNull TransformationKey key, int from, @NonNull Set<Integer> to) {
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
    return builder.createDirectedGraph();
  }

  private static <N, E> List<E> map(@NonNull List<N> list, @NonNull Function<N, E> f) {
    return list.stream().map(f).collect(Collectors.toList());
  }

  @Override
  public void notifyFor(@NonNull TransformationKey key) {
    // invalidate cache for key
    cache.remove(key);
  }
}
