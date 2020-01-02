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
package org.factcast.store.inmem;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.factcast.core.store.StateToken;
import org.factcast.core.store.TokenStore;

import lombok.NonNull;

class InMemTokenStore implements TokenStore {

    private final Map<StateToken, Map<UUID, Optional<UUID>>> tokens = Collections.synchronizedMap(
            new HashMap<>());

    private final Map<StateToken, String> namespaces = Collections.synchronizedMap(new HashMap<>());

    @NonNull
    public StateToken create(@NonNull Map<UUID, Optional<UUID>> state,
            @NonNull Optional<String> ns) {
        StateToken token = new StateToken();
        tokens.put(token, state);
        namespaces.put(token, ns.orElse(null));
        return token;
    }

    public void invalidate(@NonNull StateToken token) {
        tokens.remove(token);
        namespaces.remove(token);
    }

    @Override
    @NonNull
    public Optional<Map<UUID, Optional<UUID>>> getState(@NonNull StateToken token) {
        return Optional.ofNullable(tokens.get(token));
    }

    @Override
    @NonNull
    public Optional<String> getNs(@NonNull StateToken token) {
        return Optional.ofNullable(namespaces.get(token));
    }
}
