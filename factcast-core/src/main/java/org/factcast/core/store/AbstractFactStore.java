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
package org.factcast.core.store;

import java.util.*;

import org.factcast.core.Fact;
import org.factcast.core.spec.FactSpec;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

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
            Optional<State> state = tokenStore.get(token);

            if (state.isPresent()) {
                try {
                    if (isStateUnchanged(state.get())) {
                        publish(factsToPublish);
                        return true;
                    } else {
                        return false;
                    }
                } finally {
                    tokenStore.invalidate(token);
                }
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

    // TODO needed?
    public StateToken stateFor(@NonNull List<FactSpec> specs) {
        State state = getStateFor(specs);
        return tokenStore.create(state);
    }

    @SuppressWarnings("WeakerAccess")
    protected final boolean isStateUnchanged(
            @NonNull State snapshotState) {
        State currentState = getStateFor(snapshotState.specs());
        return currentState.serialOfLastMatchingFact() == snapshotState.serialOfLastMatchingFact();
    }

    private boolean sameValue(
            Map<UUID, Optional<UUID>> currentState,
            Map<UUID, Optional<UUID>> snapshotState, UUID k) {
        Optional<UUID> current = currentState.get(k);
        Optional<UUID> snap = snapshotState.get(k);
        if (current == null && snap == null) {
            return true;
        } else // noinspection OptionalAssignedToNull
        {
            if (current == null || snap == null) {
                return false;
            } else {
                return snap.equals(current);
            }
        }
    }

    protected abstract State getStateFor(@NonNull List<FactSpec> specs);

}
