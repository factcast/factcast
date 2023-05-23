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
package org.factcast.core.lock;

import java.util.*;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.store.FactStore;

@RequiredArgsConstructor
public final class DeprecatedLockedOperationBuilder {
  @NonNull final FactStore store;

  final String ns;

  public LockedOperationBuilder on(@NonNull UUID aggId, UUID... otherAggIds) {
    LinkedList<UUID> ids = new LinkedList<>();
    ids.add(aggId);
    ids.addAll(Arrays.asList(otherAggIds));
    return new LockedOperationBuilder(store, toFactSpecs(ids));
  }

  @NonNull
  private List<FactSpec> toFactSpecs(LinkedList<UUID> ids) {
    return ids.stream().map(i -> FactSpec.ns(ns).aggId(i)).collect(Collectors.toList());
  }

  public LockedOperationBuilder on(@NonNull Collection<UUID> aggIds) {
    LinkedList<UUID> ids = new LinkedList<>();
    ids.addAll(aggIds);
    return new LockedOperationBuilder(store, toFactSpecs(ids));
  }
}
