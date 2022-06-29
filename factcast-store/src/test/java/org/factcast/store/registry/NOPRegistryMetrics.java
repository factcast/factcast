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
package org.factcast.store.registry;

import io.micrometer.core.instrument.Tags;
import java.util.function.Supplier;
import org.factcast.store.registry.metrics.RegistryMetrics;
import org.factcast.store.registry.metrics.RunnableWithException;
import org.factcast.store.registry.metrics.SupplierWithException;

public class NOPRegistryMetrics implements RegistryMetrics {

  @Override
  public void timed(OP operation, Runnable fn) {
    fn.run();
  }

  @Override
  public void timed(OP operation, Tags tags, Runnable fn) {
    fn.run();
  }

  @Override
  public <E extends Exception> void timed(
      OP operation, Class<E> exceptionClass, RunnableWithException<E> fn) throws E {
    fn.run();
  }

  @Override
  public <E extends Exception> void timed(
      OP operation, Class<E> exceptionClass, Tags tags, RunnableWithException<E> fn) throws E {
    fn.run();
  }

  @Override
  public <T> T timed(OP operation, Supplier<T> fn) {
    return fn.get();
  }

  @Override
  public <T> T timed(OP operation, Tags tags, Supplier<T> fn) {
    return fn.get();
  }

  @Override
  public <R, E extends Exception> R timed(
      OP operation, Class<E> exceptionClass, SupplierWithException<R, E> fn) throws E {
    return fn.get();
  }

  @Override
  public <R, E extends Exception> R timed(
      OP operation, Class<E> exceptionClass, Tags tags, SupplierWithException<R, E> fn) throws E {
    return fn.get();
  }

  @Override
  public void count(EVENT event) {}

  @Override
  public void count(EVENT event, Tags tags) {}
}
