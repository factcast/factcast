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
package org.factcast.core;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;

import org.factcast.core.store.FactStore;
import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.SubscriptionRequest;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.core.subscription.observer.FactObserver;
import org.factcast.core.subscription.observer.IdObserver;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Default impl for FactCast used by FactCast.from* methods.
 *
 * @author uwe.schaefer@mercateo.com
 */
@RequiredArgsConstructor
class DefaultFactCast implements FactCast {

    @NonNull
    final FactStore store;

    @Override
    @NonNull
    public Subscription subscribeToFacts(@NonNull SubscriptionRequest req,
            @NonNull FactObserver observer) {
        return store.subscribe(SubscriptionRequestTO.forFacts(req), observer);
    }

    @Override
    @NonNull
    public Subscription subscribeToIds(@NonNull SubscriptionRequest req,
            @NonNull IdObserver observer) {
        return store.subscribe(SubscriptionRequestTO.forIds(req), observer.map(Fact::id));
    }

    @Override
    @NonNull
    public Optional<Fact> fetchById(@NonNull UUID id) {
        return store.fetchById(id);
    }

    @Override
    public void publish(@NonNull List<? extends Fact> factsToPublish) {
        factsToPublish.forEach(f -> {
            if (lacksRequiredNamespace(f))
                throw new IllegalArgumentException("Fact " + f.id() + " lacks required namespace.");
            if (lacksRequiredId(f))
                throw new IllegalArgumentException("Fact " + f.jsonHeader()
                        + " lacks required id.");
        });
        store.publish(factsToPublish);
    }

    private boolean lacksRequiredNamespace(Fact f) {
        return f.ns() == null || f.ns().trim().isEmpty();
    }

    private boolean lacksRequiredId(Fact f) {
        return f.id() == null;
    }

    @Override
    @NonNull
    public OptionalLong serialOf(@NonNull UUID id) {
        return store.serialOf(id);
    }

    @Override
    public Set<String> enumerateNamespaces() {
        return store.enumerateNamespaces();
    }

    @Override
    public Set<String> enumerateTypes(@NonNull String ns) {
        return store.enumerateTypes(ns);
    }
}
