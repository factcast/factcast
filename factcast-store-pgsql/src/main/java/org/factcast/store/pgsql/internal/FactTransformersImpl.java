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
package org.factcast.store.pgsql.internal;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import org.factcast.core.Fact;
import org.factcast.core.subscription.FactTransformers;
import org.factcast.core.subscription.TransformationException;
import org.factcast.core.util.FactCastJson;
import org.factcast.store.pgsql.registry.transformation.TransformationKey;
import org.factcast.store.pgsql.registry.transformation.cache.TransformationCache;
import org.factcast.store.pgsql.registry.transformation.chains.TransformationChain;
import org.factcast.store.pgsql.registry.transformation.chains.TransformationChains;
import org.factcast.store.pgsql.registry.transformation.chains.Transformer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class FactTransformersImpl implements FactTransformers {

    @NonNull
    private final RequestedVersions requestedVersions;

    @NonNull
    private final TransformationChains chains;

    @NonNull
    private final Transformer trans;

    @NonNull
    private final TransformationCache cache;

    @Override
    public @NonNull Fact transformIfNecessary(@NonNull Fact e) throws TransformationException {

        Set<Integer> requested = requestedVersions.get(e.ns(), e.type());

        if (clientDoesNotCare(requested) || clientExpectsVersion(requested, e)) {
            return e;
        } else
            return transform(requested, e);
    }

    private @NonNull Fact transform(Set<Integer> requested, @NonNull Fact e)
            throws TransformationException {
        // find the "best" version if there are more than one requested
        OptionalInt max = requested.stream().mapToInt(v -> v).max();
        int targetVersion = max.orElseThrow(() -> new IllegalArgumentException(
                "No reqested Version !? This must not happen"));
        int sourceVersion = e.version();

        TransformationKey key = TransformationKey.builder()
                .ns(e.ns())
                .type(e.type())
                .build();
        TransformationChain chain = chains.get(key, sourceVersion,
                targetVersion);

        String chainId = chain.meta().id();

        Optional<Fact> cached = cache.find(e.id(), targetVersion, chainId);
        if (cached.isPresent())
            return cached.get();
        else {
            JsonNode input = FactCastJson.valueToTree(e);
            try {
                Fact transformed = FactCastJson.treeToFact(trans.transform(chain, input));
                cache.put(transformed, chainId);
                return transformed;
            } catch (JsonProcessingException e1) {
                throw new TransformationException(e1);
            }
        }
    }

    private boolean clientExpectsVersion(Set<Integer> requested, Fact e) {
        return requested.contains(e.version()) || requested.contains(0);
    }

    private boolean clientDoesNotCare(Set<Integer> requested) {
        return requested.isEmpty();
    }

}
