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

public enum RegistryMetricsEvent {
  TRANSFORMATION_CACHE_HIT("transformationCache-hit"),
  TRANSFORMATION_CACHE_MISS("transformationCache-miss"),
  MISSING_TRANSFORMATION_INFO("missingTransformationInformation"),
  TRANSFORMATION_CONFLICT("transformationConflict"),
  REGISTRY_FILE_FETCH_FAILED("registryFileFetchFailed"),
  SCHEMA_REGISTRY_UNAVAILABLE("schemaRegistryUnavailable"),
  TRANSFORMATION_FAILED("transformationFailed"),
  SCHEMA_CONFLICT("schemaConflict"),
  FACT_VALIDATION_FAILED("factValidationFailed"),
  SCHEMA_MISSING("schemaMissing");

  @NonNull @Getter final String event;

  RegistryMetricsEvent(@NonNull String event) {
    this.event = event;
  }
}
