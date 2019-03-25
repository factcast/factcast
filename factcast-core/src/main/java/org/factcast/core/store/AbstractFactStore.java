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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.factcast.core.Fact;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public abstract class AbstractFactStore implements FactStore {
    protected final TokenStore tokenStore;

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

    protected abstract boolean isStateUnchanged(String ns, Map<UUID, Optional<UUID>> state);

}
