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
package org.factcast.core.store;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.factcast.core.Fact;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
@RequiredArgsConstructor
public abstract class AbstractFactStore implements FactStore {
    @NonNull
    protected final TokenStore tokenStore;

    @Override
    public boolean publishIfUnchanged(
            @NonNull List<? extends Fact> factsToPublish,
            @NonNull Optional<StateToken> optionalToken) {

        if (optionalToken.isPresent()) {
            StateToken token = optionalToken.get();
            Optional<String> ns = tokenStore.getNs(token);
            Optional<Map<UUID, Optional<UUID>>> state = tokenStore.getState(token);

            if (state.isPresent()) {
                if (isStateUnchanged(ns, state.get())) {
                    publish(factsToPublish);
                    tokenStore.invalidate(token);
                    return true;
                } else
                    return false;
            } else {
                // token is unknown, just reject.
                return false;
            }
        } else {
            // publish unconditionally
            publish(factsToPublish);
            return true;
        }
    }

    @Override
    public void invalidate(@NonNull StateToken token) {
        tokenStore.invalidate(token);
    }

    @Override
    public StateToken stateFor(@NonNull Collection<UUID> forAggIds,
            @NonNull Optional<String> ns) {
        Map<UUID, Optional<UUID>> state = getStateFor(ns, forAggIds);
        return tokenStore.create(state, ns);
    }

    @SuppressWarnings("WeakerAccess")
    protected final boolean isStateUnchanged(@NonNull Optional<String> ns,
            @NonNull Map<UUID, Optional<UUID>> snapshotState) {
        Map<UUID, Optional<UUID>> currentState = getStateFor(ns, snapshotState
                .keySet());

        if (currentState.size() == snapshotState.size()) {
            for (UUID k : currentState.keySet()) {
                if (!sameValue(currentState, snapshotState, k))
                    return false;
            }
            return true;
        } else
            return false;
    }

    @SuppressWarnings("OptionalAssignedToNull")
    private boolean sameValue(Map<UUID, Optional<UUID>> currentState,
            Map<UUID, Optional<UUID>> snapshotState, UUID k) {
        Optional<UUID> current = currentState.get(k);
        Optional<UUID> snap = snapshotState.get(k);
        if (current == null && snap == null)
            return true;
        else // noinspection OptionalAssignedToNull
        if (current == null || snap == null)
            return false;
        else
            return snap.equals(current);
    }

    protected abstract Map<UUID, Optional<UUID>> getStateFor(@NonNull Optional<String> ns,
            @NonNull Collection<UUID> forAggIds);

}
