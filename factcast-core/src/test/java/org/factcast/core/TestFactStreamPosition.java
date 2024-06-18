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

import java.util.Random;
import java.util.UUID;
import lombok.NonNull;

public class TestFactStreamPosition {

  private static final Random random = new Random();

  @NonNull
  public static FactStreamPosition random() {
    return FactStreamPosition.of(UUID.randomUUID(), Math.abs(random.nextLong()) + 1);
  }

  @NonNull
  public static FactStreamPosition fromString(@NonNull String uuid) {
    return FactStreamPosition.of(UUID.fromString(uuid), Math.abs(random.nextLong()) + 1);
  }
}
