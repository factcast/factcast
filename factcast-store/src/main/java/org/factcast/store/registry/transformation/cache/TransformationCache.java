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
import java.util.Optional;
import java.util.UUID;
import org.factcast.core.Fact;

public interface TransformationCache {

  // maybe optimize by passing header and payload separately as
  // string/jsonnode?
  void put(Fact f, String transformationChainId);

  Optional<Fact> find(UUID eventId, int version, String transformationChainId);

  void compact(ZonedDateTime thresholdDate);
}
