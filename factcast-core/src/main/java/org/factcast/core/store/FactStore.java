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

import java.time.LocalDate;
import java.util.*;
import lombok.NonNull;
import org.factcast.core.Fact;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.core.subscription.TransformationException;
import org.factcast.core.subscription.observer.FactObserver;

/**
 * A read/Write FactStore.
 *
 * <p>Where FactCast is an interface to work with as an application, FactStore is something that
 * FactCast impls use to actually store and retrieve Facts.
 *
 * <p>In a sense it is an internal interface, or SPI implemented by for instance InMemFactStore or
 * PgFactStore.
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

  @NonNull
  Set<Integer> enumerateVersions(@NonNull String ns, @NonNull String type);

  boolean publishIfUnchanged(
      @NonNull List<? extends Fact> factsToPublish, @NonNull Optional<StateToken> token);

  @NonNull
  StateToken stateFor(@NonNull List<FactSpec> specs);

  void invalidate(@NonNull StateToken token);

  long currentTime();

  @NonNull
  Optional<Fact> fetchById(@NonNull UUID id);

  @NonNull
  Optional<Fact> fetchByIdAndVersion(@NonNull UUID id, int versionExpected)
      throws TransformationException;

  @NonNull
  StateToken currentStateFor(List<FactSpec> factSpecs);

  /**
   * @return 0 if the store is empty
   * @since 0.7.3
   */
  long latestSerial();

  /**
   * @return 0 if the store is empty
   * @since 0.7.3
   */
  long lastSerialBefore(@NonNull LocalDate date);

  /**
   * @return the last available serial if the day is today or in the future or null if there is no
   *     facts
   * @since 0.9.0
   */
  Long firstSerialAfter(@NonNull LocalDate date);

  /**
   * @param serial to look for
   * @return the Fact stored with that serial or empty if it does not exist
   * @since 0.7.3
   */
  Optional<Fact> fetchBySerial(long serial);
}
