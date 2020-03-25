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
package org.factcast.store.pgsql.registry.transformation;

import java.util.Optional;
import java.util.OptionalInt;

import org.factcast.core.Fact;
import org.factcast.core.subscription.FactTransformers;
import org.factcast.core.subscription.TransformationException;
import org.factcast.core.util.FactCastJson;
import org.factcast.store.pgsql.internal.RequestedVersions;
import org.factcast.store.pgsql.registry.transformation.cache.TransformationCache;
import org.factcast.store.pgsql.registry.transformation.chains.TransformationChain;
import org.factcast.store.pgsql.registry.transformation.chains.TransformationChains;
import org.factcast.store.pgsql.registry.transformation.chains.Transformer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.annotations.VisibleForTesting;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class FactTransformersImpl implements FactTransformers {

    @NonNull
    private final RequestedVersions requested;

    @NonNull
    private final TransformationChains chains;

    @NonNull
    private final Transformer trans;

    @NonNull
    private final TransformationCache cache;

    @Override
    public @NonNull Fact transformIfNecessary(@NonNull Fact e) throws TransformationException {

        String ns = e.ns();
        String type = e.type();

        if (type == null || requested.dontCare(ns, type) || requested.exactVersion(ns, type, e
                .version())) {
            return e;
        } else {
            OptionalInt max = requested.get(ns, type).stream().mapToInt(v -> v).max();
            int targetVersion = max.orElseThrow(() -> new IllegalArgumentException(
                    "No reqested Version !? This must not happen"));
            return transform(targetVersion, e);
        }

    }

    @VisibleForTesting
    protected @NonNull Fact transform(int targetVersion, @NonNull Fact e)
            throws TransformationException {
        // find the "best" version if there are more than one requested

        int sourceVersion = e.version();

        TransformationKey key = TransformationKey.of(e.ns(), e.type());
        TransformationChain chain = chains.get(key, sourceVersion,
                targetVersion);

        String chainId = chain.id();

        Optional<Fact> cached = cache.find(e.id(), targetVersion, chainId);
        if (cached.isPresent())
            return cached.get();
        else {
            try {
                JsonNode input = FactCastJson.readTree(e.jsonPayload());
                JsonNode header = FactCastJson.readTree(e.jsonHeader());
                ((ObjectNode) header).put("version", targetVersion);
                JsonNode transformedPayload = trans.transform(chain, input);
                Fact transformed = Fact.of(header, transformedPayload);
                // can be optimized by passing jsonnode?
                cache.put(transformed, chainId);
                return transformed;
            } catch (JsonProcessingException e1) {
                throw new TransformationException(e1);
            }
        }
    }

}
