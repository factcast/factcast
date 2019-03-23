/*
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
package org.factcast.store.inmem;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.UUID;

import org.factcast.core.Fact;
import org.factcast.core.store.FactStore;
import org.factcast.core.store.StateToken;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor

public abstract class AbstractFactStore implements FactStore {
    protected final TokenStore tokenStore;

    protected abstract Optional<UUID> latestFactFor(String ns, UUID aggId);

    @Override
    public boolean publishIfUnchanged(@NonNull StateToken token,
            @NonNull List<? extends Fact> factsToPublish) {

        String ns = tokenStore.getNs(token);
        if (isStateUnchanged(ns, tokenStore.getState(token))) {
            publish(factsToPublish);
            tokenStore.invalidate(token);
            return true;
        } else
            return false;
    }

    private boolean isStateUnchanged(@NonNull String ns, @NonNull Map<UUID, Optional<UUID>> state) {
        for (Entry<UUID, Optional<UUID>> e : state.entrySet()) {
            if (!latestFactFor(ns, e.getKey()).equals(e.getValue()))
                return false;
        }
        return true;
    }

}
