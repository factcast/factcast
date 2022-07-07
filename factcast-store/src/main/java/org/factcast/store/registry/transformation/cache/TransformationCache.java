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
package org.factcast.store.registry.transformation.cache;

import java.time.ZonedDateTime;
import java.util.*;
import lombok.NonNull;
import lombok.Value;
import org.factcast.core.Fact;

public interface TransformationCache {

  // maybe optimize by passing header and payload separately as
  // string/jsonnode?
  void put(@NonNull TransformationCache.Key key, @NonNull Fact f);

  Optional<Fact> find(Key key);

  Set<Fact> findAll(Collection<Key> keys);

  void compact(ZonedDateTime thresholdDate);

  @Value
  class Key {

    String id;

    public static Key of(@NonNull Fact fact, @NonNull String transformationChainId) {
      return of(fact.id(), fact.version(), transformationChainId);
    }

    public static Key of(@NonNull UUID id, int version, @NonNull String transformationChainId) {
      return new Key(
          String.join("-", id.toString(), String.valueOf(version), transformationChainId));
    }
  }
}
