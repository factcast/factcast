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
package org.factcast.store.pgsql.registry.transformation.cache;

import java.util.Map;
import java.util.Optional;

import org.apache.commons.collections15.map.LRUMap;
import org.factcast.core.Fact;
import org.joda.time.DateTime;

public class InMemTransformationCache implements TransformationCache {

    private static final int CAPACITY = 1_000_000;

    private Map<String, Fact> cache = new LRUMap<>(
            InMemTransformationCache.CAPACITY);

    @Override
    public void put(Fact f, String transformationChainId) {
        String key = String.join(",", f.ns(), f.type(), String.valueOf(f.version()),
                transformationChainId);
        cache.put(key, f);
    }

    @Override
    public Optional<Fact> find(String ns, String type, int version, String transformationChainId) {
        String key = String.join(",", ns, type, String.valueOf(version),
                transformationChainId);
        return Optional.ofNullable(cache.get(key));
    }

    @Override
    public void compact(DateTime thresholdDate) {
        // TODO Auto-generated method stub
    }

}
