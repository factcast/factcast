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

import io.micrometer.core.instrument.Tags;
import java.util.function.Supplier;

public interface RegistryMetrics {
  String TAG_STATUS_CODE_KEY = "code";

  String TAG_IDENTITY_KEY = "id";

  void timed(TimedOperation operation, Runnable fn);

  void timed(TimedOperation operation, Tags tags, Runnable fn);

  <E extends Exception> void timed(
      TimedOperation operation, Class<E> exceptionClass, RunnableWithException<E> fn) throws E;

  <E extends Exception> void timed(
      TimedOperation operation, Class<E> exceptionClass, Tags tags, RunnableWithException<E> fn)
      throws E;

  <T> T timed(TimedOperation operation, Supplier<T> fn);

  <T> T timed(TimedOperation operation, Tags tags, Supplier<T> fn);

  <R, E extends Exception> R timed(
      TimedOperation operation, Class<E> exceptionClass, SupplierWithException<R, E> fn) throws E;

  <R, E extends Exception> R timed(
      TimedOperation operation, Class<E> exceptionClass, Tags tags, SupplierWithException<R, E> fn)
      throws E;

  void count(MetricEvent event);

  void count(MetricEvent event, Tags tags);
}
