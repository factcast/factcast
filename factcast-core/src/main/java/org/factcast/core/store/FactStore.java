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
package org.factcast.core.store;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;

import org.factcast.core.Fact;
import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.core.subscription.observer.FactObserver;

import lombok.NonNull;

/**
 * A read/Write FactStore.
 *
 * Where FactCast is an interface to work with as an application, FactStore is
 * something that FactCast impls use to actually store and retrieve Facts.
 *
 * In a sense it is an internal interface, or SPI implemented by for instance
 * InMemFactStore or PGFactStore.
 *
 * @author uwe.schaefer@mercateo.com
 */
public interface FactStore {

    void publish(List<? extends Fact> factsToPublish);

    Subscription subscribe(SubscriptionRequestTO request, FactObserver observer);

    Optional<Fact> fetchById(UUID id);

    OptionalLong serialOf(UUID l);

    // see #153
    Set<String> enumerateNamespaces();

    Set<String> enumerateTypes(@NonNull String ns);
}
