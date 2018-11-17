/**
 * Copyright © 2018 Mercateo AG (http://www.mercateo.com)
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
package org.factcast.client.cache;

import java.util.Optional;
import java.util.UUID;

import org.factcast.core.Fact;
import org.factcast.core.store.FactStore;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * A cacheable wrapper for a lookup of facts by their id.
 *
 * Not intended for direct usage from with application code. This is used by the
 * CachingFactCast wrapper as a strategy to lookup facts.
 *
 * @author <uwe.schaefer@mercateo.com>
 */
@Component
@RequiredArgsConstructor
public class CachingFactLookup {

    public static final String CACHE_NAME = "factcast.lookup.fact";

    @NonNull
    final FactStore store;

    @Cacheable(CACHE_NAME)
    public Optional<Fact> lookup(UUID id) {
        return store.fetchById(id);
    }
}
