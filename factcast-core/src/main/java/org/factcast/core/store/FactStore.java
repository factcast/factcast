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

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;

import org.factcast.core.Fact;
import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.core.subscription.TransformationException;
import org.factcast.core.subscription.observer.FactObserver;

import lombok.NonNull;

/**
 * A read/Write FactStore.
 *
 * Where FactCast is an interface to work with as an application, FactStore is
 * something that FactCast impls use to actually store and retrieve Facts.
 *
 * In a sense it is an internal interface, or SPI implemented by for instance
 * InMemFactStore or PgFactStore.
 *
 * @author uwe.schaefer@prisma-capacity.eu
 */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public interface FactStore {

    void publish(@NonNull List<? extends Fact> factsToPublish);

    @NonNull
    Subscription subscribe(@NonNull SubscriptionRequestTO request, @NonNull FactObserver observer);

    @NonNull
    OptionalLong serialOf(@NonNull UUID l);

    // see #153
    @NonNull
    Set<String> enumerateNamespaces();

    @NonNull
    Set<String> enumerateTypes(@NonNull String ns);

    boolean publishIfUnchanged(@NonNull List<? extends Fact> factsToPublish,
            @NonNull Optional<StateToken> token);

    @NonNull
    StateToken stateFor(@NonNull Collection<UUID> forAggIds, @NonNull Optional<String> ns);

    void invalidate(@NonNull StateToken token);

    long currentTime();

    @NonNull
    Optional<Fact> fetchById(@NonNull UUID id);

    @NonNull
    Optional<Fact> fetchByIdAndVersion(@NonNull UUID id, int versionExpected)
            throws TransformationException;
}
