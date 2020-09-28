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

public enum MetricEvent {
  TRANSFORMATION_CACHE_HIT("transformation_cache_hit"),
  TRANSFORMATION_CACHE_MISS("transformation_cache_miss"),
  MISSING_TRANSFORMATION_INFO("missing_transformation_information"),
  TRANSFORMATION_CONFLICT("transformation_conflict"),
  REGISTRY_FILE_FETCH_FAILED("registry_file_fetch_failed"),
  SCHEMA_REGISTRY_UNAVAILABLE("schema_registry_unavailable"),
  TRANSFORMATION_FAILED("transformation_failed"),
  SCHEMA_CONFLICT("schema_conflict"),
  FACT_VALIDATION_FAILED("fact_validation_failed"),
  SCHEMA_MISSING("schema_missing");

  @NonNull @Getter final String event;

  MetricEvent(@NonNull String event) {
    this.event = event;
  }
}
