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
package org.factcast.core;

import java.util.*;

import org.factcast.core.lock.*;
import org.factcast.core.store.*;
import org.factcast.core.subscription.*;
import org.factcast.core.subscription.observer.*;

import lombok.*;

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
        FactValidation.validate(factsToPublish);
        store.publish(factsToPublish);
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

    @Override
    public LockedOperationBuilder lock(@NonNull String ns) {
        if (ns.trim().isEmpty())
            throw new IllegalArgumentException("Namespace must not be empty");
        return new LockedOperationBuilder(this.store, ns);
    }

    @Override
    public LockedOperationBuilder lockGlobally() {
        return new LockedOperationBuilder(this.store, null);
    }
}
