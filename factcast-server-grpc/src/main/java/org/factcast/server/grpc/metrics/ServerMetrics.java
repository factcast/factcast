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
package org.factcast.server.grpc.metrics;

import io.micrometer.core.instrument.Tags;
import java.util.function.Supplier;
import lombok.Getter;
import lombok.NonNull;
import org.factcast.factus.metrics.RunnableWithException;
import org.factcast.factus.metrics.SupplierWithException;

public interface ServerMetrics {


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

  enum OP {
    HANDSHAKE("handshake");

    @NonNull @Getter final String op;

    OP(@NonNull String op) {
      this.op = op;
    }
  }

  enum EVENT {
    SOME_EVENT_CHANGE_ME("something");

    @NonNull @Getter final String event;

    EVENT(@NonNull String event) {
      this.event = event;
    }
  }

}
