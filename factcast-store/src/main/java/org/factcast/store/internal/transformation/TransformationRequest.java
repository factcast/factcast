/*
 * Copyright © 2017-2022 factcast.org
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
package org.factcast.store.internal.transformation;

import java.util.*;
import lombok.Getter;
import lombok.NonNull;
import org.factcast.store.internal.PgFact;

public class TransformationRequest {
  @Getter private @NonNull PgFact toTransform;
  @Getter private final Set<Integer> targetVersions;

  public TransformationRequest(@NonNull PgFact toTransform, Set<Integer> targetVersions) {
    this.toTransform = toTransform;
    this.targetVersions = targetVersions;
  }

  /**
   * Returns the PgFact and clears the internal reference so the original fact can be GC'd earlier.
   */
  public @NonNull PgFact consumeToTransform() {
    PgFact ref = toTransform;
    if (ref == null) {
      throw new IllegalStateException("PgFact already consumed");
    }
    toTransform = null;
    return ref;
  }
}
