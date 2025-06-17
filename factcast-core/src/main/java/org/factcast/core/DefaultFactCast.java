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
package org.factcast.core;

import java.util.*;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.factcast.core.lock.LockedOperationBuilder;
import org.factcast.core.lock.MultiAggregateLockedOperationBuilder;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.store.FactStore;
import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.SubscriptionRequest;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.core.subscription.TransformationException;
import org.factcast.core.subscription.observer.FactObserver;

/**
 * Default impl for FactCast used by FactCast.from* methods.
 *
 * @author uwe.schaefer@prisma-capacity.eu
 */
@RequiredArgsConstructor
class DefaultFactCast implements FactCast {

  @NonNull final FactStore store;

  @Override
  @NonNull
  public Subscription subscribeEphemeral(
      @NonNull SubscriptionRequest req, @NonNull FactObserver observer) {
    return store.subscribe(SubscriptionRequestTO.from(req), observer);
  }

  @Override
  public void publish(@NonNull List<? extends Fact> factsToPublish) {
    FactValidation.validateOnPublish(factsToPublish);
    store.publish(factsToPublish);
  }

  @Override
  @NonNull
  public LockedOperationBuilder lock(@NonNull List<FactSpec> scope) {
    return new LockedOperationBuilder(store, scope);
  }

  @Override
  @NonNull
  public OptionalLong serialOf(@NonNull UUID id) {
    return store.serialOf(id);
  }

  @Override
  @NonNull
  public Set<String> enumerateNamespaces() {
    return store.enumerateNamespaces();
  }

  @Override
  @NonNull
  public Set<String> enumerateTypes(@NonNull String ns) {
    return store.enumerateTypes(ns);
  }

  @Override
  @NonNull
  public Set<Integer> enumerateVersions(@NonNull String ns, @NonNull String type) {
    return store.enumerateVersions(ns, type);
  }

  @Override
  @NonNull
  public MultiAggregateLockedOperationBuilder lock(@NonNull String ns) {
    if (ns.trim().isEmpty()) {
      throw new IllegalArgumentException("Namespace must not be empty");
    }
    return new MultiAggregateLockedOperationBuilder(store, ns);
  }

  @Override
  @NonNull
  public Subscription subscribe(
      @NonNull SubscriptionRequest request, @NonNull FactObserver observer) {
    return store.subscribe(SubscriptionRequestTO.from(request), observer);
  }

  @Override
  @NonNull
  public Optional<Fact> fetchById(@NonNull UUID id) {
    return store.fetchById(id);
  }

  @Override
  @NonNull
  public Optional<Fact> fetchByIdAndVersion(@NonNull UUID id, int versionExpected)
      throws TransformationException {
    return store.fetchByIdAndVersion(id, versionExpected);
  }

  @Override
  @NonNull
  public FactStore store() {
    return store;
  }
}
