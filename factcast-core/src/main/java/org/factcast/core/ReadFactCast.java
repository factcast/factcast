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

import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import lombok.NonNull;
import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.SubscriptionRequest;
import org.factcast.core.subscription.TransformationException;
import org.factcast.core.subscription.observer.FactObserver;

/**
 * A read-only interface to a FactCast, that only offers subscription and Fact-by-id lookup.
 *
 * @author uwe.schaefer@prisma-capacity.eu
 */
public interface ReadFactCast {

  /** Same as subscribeToFacts, but adds automatic reconnection. */
  @NonNull
  Subscription subscribe(@NonNull SubscriptionRequest request, @NonNull FactObserver observer);

  @NonNull
  Subscription subscribeEphemeral(
      @NonNull SubscriptionRequest request, @NonNull FactObserver observer);

  @NonNull
  OptionalLong serialOf(@NonNull UUID id);

  @NonNull
  Optional<Fact> fetchById(@NonNull UUID id);

  @NonNull
  Optional<Fact> fetchByIdAndVersion(@NonNull UUID id, int versionExpected)
      throws TransformationException;

  // see #153
  @NonNull
  Set<String> enumerateNamespaces();

  @NonNull
  Set<String> enumerateTypes(@NonNull String ns);

  @NonNull
  @Deprecated(since = "0.5.6", forRemoval = true)
  ReadFactCast retry(int maxAttempts);

  @NonNull
  @Deprecated(since = "0.5.6", forRemoval = true)
  ReadFactCast retry(int maxAttempts, long minimumWaitIntervalMillis);
}
