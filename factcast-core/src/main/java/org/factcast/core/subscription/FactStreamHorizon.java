/*
 * Copyright © 2017-2026 factcast.org
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
package org.factcast.core.subscription;

import lombok.NonNull;

// Carries the inclusive fact serial bound read from the database for this query.
public record FactStreamHorizon(long factSerial) {

  public static FactStreamHorizon empty() {
    return new FactStreamHorizon(0);
  }

  public static FactStreamHorizon min(
      @NonNull FactStreamHorizon left, @NonNull FactStreamHorizon right) {
    long l = left.factSerial();
    long r = right.factSerial();

    return (l < r) ? left : right;
  }
}
