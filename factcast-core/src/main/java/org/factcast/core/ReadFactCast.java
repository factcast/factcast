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

import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;

import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.SubscriptionRequest;
import org.factcast.core.subscription.observer.FactObserver;
import org.factcast.core.subscription.observer.IdObserver;

import lombok.NonNull;

/**
 * A read-only interface to a FactCast, that only offers subscription and
 * Fact-by-id lookup.
 *
 * @author uwe.schaefer@mercateo.com
 */
public interface ReadFactCast {

    Subscription subscribeToFacts(@NonNull SubscriptionRequest request,
            @NonNull FactObserver observer);

    Subscription subscribeToIds(@NonNull SubscriptionRequest request, @NonNull IdObserver observer);

    Optional<Fact> fetchById(@NonNull UUID id);

    OptionalLong serialOf(@NonNull UUID id);

    // see #153
    Set<String> enumerateNamespaces();

    Set<String> enumerateTypes(@NonNull String ns);
}
