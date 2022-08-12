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
package org.factcast.factus.metrics;

import io.micrometer.core.instrument.Tags;
import java.util.function.*;
import org.factcast.core.util.RunnableWithException;
import org.factcast.core.util.SupplierWithException;

public interface FactusMetrics {

  void timed(TimedOperation operation, Tags tags, Runnable fn);

  void timed(TimedOperation operation, Tags tags, long milliseconds);

  <E extends Exception> void timed(
      TimedOperation operation, Class<E> exceptionClass, RunnableWithException<E> fn) throws E;

  <E extends Exception> void timed(
      TimedOperation operation, Class<E> exceptionClass, Tags tags, RunnableWithException<E> fn)
      throws E;

  <T> T timed(TimedOperation operation, Tags tags, Supplier<T> fn);

  <R, E extends Exception> R timed(
      TimedOperation operation, Class<E> exceptionClass, Tags tags, SupplierWithException<R, E> fn)
      throws E;

  void count(CountedEvent event, Tags tags);

  void record(GaugedEvent event, Tags tags, long value);
}
