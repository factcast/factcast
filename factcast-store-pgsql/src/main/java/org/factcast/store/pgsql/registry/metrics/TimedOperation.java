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
package org.factcast.store.pgsql.registry.metrics;

import lombok.Getter;
import lombok.NonNull;

public enum TimedOperation {
  REFRESH_REGISTRY("refresh_registry"),
  COMPACT_TRANSFORMATION_CACHE("compact_transformation_cache"),
  TRANSFORMATION("transform_event"),
  FETCH_REGISTRY_FILE("fetch_registry_file");

  @NonNull @Getter final String op;

  TimedOperation(@NonNull String op) {
    this.op = op;
  }
}
