/*
 * Copyright © 2017-2024 factcast.org
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
import java.util.UUID;
import javax.annotation.Nullable;
import lombok.NonNull;
import lombok.Value;

@Value(staticConstructor = "of")
public class FactStreamPosition {
  /** will always be used to reference a certain state */
  @Nullable UUID factId;

  /** only to be used for checking before/after relation between facts. */
  long serial;

  public static FactStreamPosition from(@NonNull Fact f) {
    return of(f.header().id(), Optional.ofNullable(f.header().serial()).orElse(-1L));
  }

  /**
   * @param uuid
   * @deprecated should only be relevant for tests, and during migration
   */
  @Deprecated
  // for compatibility only
  public static FactStreamPosition withoutSerial(@Nullable UUID uuid) {
    if (uuid == null) return null;
    else return FactStreamPosition.of(uuid, -1L);
  }
}
