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
package org.factcast.store.registry.metrics;

import io.micrometer.core.instrument.Tags;
import java.util.concurrent.ExecutorService;
import java.util.function.*;
import lombok.Getter;
import lombok.NonNull;
import org.factcast.core.util.RunnableWithException;
import org.factcast.core.util.SupplierWithException;

public interface RegistryMetrics {
  String TAG_STATUS_CODE_KEY = "code";

  String TAG_IDENTITY_KEY = "id";

  void timed(OP operation, Runnable fn);

  void timed(OP operation, Tags tags, Runnable fn);

  <E extends Exception> void timed(
      OP operation, Class<E> exceptionClass, RunnableWithException<E> fn) throws E;

  <E extends Exception> void timed(
      OP operation, Class<E> exceptionClass, Tags tags, RunnableWithException<E> fn) throws E;

  <T> T timed(OP operation, Supplier<T> fn);

  <T> T timed(OP operation, Tags tags, Supplier<T> fn);

  <R, E extends Exception> R timed(
      OP operation, Class<E> exceptionClass, SupplierWithException<R, E> fn) throws E;

  <R, E extends Exception> R timed(
      OP operation, Class<E> exceptionClass, Tags tags, SupplierWithException<R, E> fn) throws E;

  void count(EVENT event);

  void count(EVENT event, Tags tags);

  void increase(EVENT transformationCacheHit, int hits);

  ExecutorService monitor(ExecutorService executor, String name);

  enum OP {
    REFRESH_REGISTRY("refreshRegistry"),
    COMPACT_TRANSFORMATION_CACHE("compactTransformationCache"),
    TRANSFORMATION("transformEvent"),
    FETCH_REGISTRY_FILE("fetchRegistryFile");

    @NonNull @Getter final String op;

    OP(@NonNull String op) {
      this.op = op;
    }
  }

  enum EVENT {
    TRANSFORMATION_CACHE_HIT("transformationCache-hit"),
    TRANSFORMATION_CACHE_MISS("transformationCache-miss"),
    MISSING_TRANSFORMATION_INFO("missingTransformationInformation"),
    TRANSFORMATION_CONFLICT("transformationConflict"),
    REGISTRY_FILE_FETCH_FAILED("registryFileFetchFailed"),
    SCHEMA_REGISTRY_UNAVAILABLE("schemaRegistryUnavailable"),
    TRANSFORMATION_FAILED("transformationFailed"),
    SCHEMA_CONFLICT("schemaConflict"),
    FACT_VALIDATION_FAILED("factValidationFailed"),
    SCHEMA_MISSING("schemaMissing"),
    SCHEMA_UPDATE_FAILURE("schemaUpdateFailure");

    @NonNull @Getter final String event;

    EVENT(@NonNull String event) {
      this.event = event;
    }
  }
}
