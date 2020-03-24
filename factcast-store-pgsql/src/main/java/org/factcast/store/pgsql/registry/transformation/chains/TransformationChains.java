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
package org.factcast.store.pgsql.registry.transformation.chains;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.factcast.store.pgsql.registry.SchemaRegistry;
import org.factcast.store.pgsql.registry.transformation.Transformation;
import org.factcast.store.pgsql.registry.transformation.TransformationKey;
import org.factcast.store.pgsql.registry.transformation.TransformationStoreListener;

import com.google.common.collect.Iterables;

import es.usc.citius.hipster.algorithm.AStar;
import es.usc.citius.hipster.algorithm.Algorithm;
import es.usc.citius.hipster.algorithm.Algorithm.SearchResult;
import es.usc.citius.hipster.algorithm.Hipster;
import es.usc.citius.hipster.graph.GraphBuilder;
import es.usc.citius.hipster.graph.GraphSearchProblem;
import es.usc.citius.hipster.graph.HipsterDirectedGraph;
import es.usc.citius.hipster.model.HeuristicNode;
import es.usc.citius.hipster.model.impl.WeightedNode;
import lombok.Value;

public class TransformationChains implements TransformationStoreListener {

    private static final double BASE_COST = 1_000_000d;

    private final SchemaRegistry r;

    private final Map<TransformationKey, Map<VersionPath, TransformationChain>> cache = new HashMap<>();

    @Value
    static class VersionPath {
        int fromVersion;

        int toVersion;
    }

    public TransformationChains(SchemaRegistry r) {
        this.r = r;
        r.register(this);
    }

    @Value(staticConstructor = "of")
    private static class Edge {
        int fromVersion;

        int toVersion;

        Transformation transformation;

        public static Edge from(Transformation t) {
            return of(t.fromVersion(), t.toVersion(), t);
        }
    }

    public TransformationChain get(TransformationKey key, int from, int to)
            throws MissingTransformationInformation {

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
            return chainsPerKey.computeIfAbsent(new VersionPath(from, to), p -> build(key, from,
                    to));
        }
    }

    @SuppressWarnings("unchecked")
    private TransformationChain build(TransformationKey key, int from, int to)
            throws MissingTransformationInformation {

        GraphBuilder<Integer, Edge> builder = GraphBuilder.create();
        List<Transformation> all = r.get(key);
        if (all.isEmpty())
            throw new MissingTransformationInformation("No Transformations for " + key);

        // populate graph
        for (Transformation t : all) {
            builder.connect(t.fromVersion()).to(t.toVersion()).withEdge(Edge.from(t));
        }
        HipsterDirectedGraph<Integer, Edge> g = builder.createDirectedGraph();

        // create problem
        AStar<Edge, Integer, Double, WeightedNode<Edge, Integer, Double>> problem = Hipster
                .createDijkstra(GraphSearchProblem.startingFrom(from)
                        .in(g)
                        .extractCostFromEdges(e -> e.fromVersion() * (e.toVersion() - e
                                .fromVersion()) + BASE_COST)
                        .build());

        // run search
        @SuppressWarnings("rawtypes")
        SearchResult r = problem.search(to);

        List<Edge> path = Algorithm.recoverActionPath(r.getGoalNode());

        if (path.isEmpty() || Iterables.getLast(path).toVersion() != to
                || Iterables.getFirst(path, null).fromVersion() != from) {
            throw new MissingTransformationInformation(
                    "Cannot reach version " + to + " from version " + from + " for " + key);
        }
        List<Transformation> steps = map(path, Edge::transformation);

        int cost = ((Double) ((HeuristicNode<?, ?, ?, ?>) r.getGoalNode()).getCost()).intValue();

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
