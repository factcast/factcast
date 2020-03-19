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
package org.factcast.store.pgsql.registry.transformation.store;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import org.factcast.store.pgsql.registry.transformation.*;

import lombok.NonNull;

public class InMemTransformationStoreImpl implements TransformationStore {
    private final Map<String, String> id2hashMap = new HashMap<>();

    private final Map<TransformationKey, List<Transformation>> transformationCache = new HashMap<>();

    @Override
    public void register(@NonNull TransformationSource source, String transformation)
            throws TransformationConflictException {
        String oldHash = id2hashMap.putIfAbsent(source.id(), source.hash());
        if (oldHash != null && !oldHash.contentEquals(source.hash()))
            throw new TransformationConflictException("Key " + source
                    + " does not match the stored hash "
                    + oldHash);

        List<Transformation> transformations = get(source.toKey());

        transformations.add(Transformation.of(source, transformation));
    }

    @Override
    public boolean contains(@NonNull TransformationSource source)
            throws TransformationConflictException {
        String hash = id2hashMap.get(source.id());
        if (hash != null)
            if (hash.equals(source.hash()))
                return true;
            else
                throw new TransformationConflictException(
                        "TransformationSource at " + source + " does not match the stored hash "
                                + hash);
        else
            return false;
    }

    private final Object mutex = new Object();

    @Override
    public List<Transformation> get(@NonNull TransformationKey key) {
        synchronized (mutex) {
            return transformationCache.computeIfAbsent(key, (k) -> new CopyOnWriteArrayList<>());
        }
    }
}
