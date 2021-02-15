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

import com.google.common.base.Stopwatch;
import io.micrometer.core.instrument.*;
import java.util.function.Supplier;
import lombok.NonNull;
import lombok.val;

public class RegistryMetricsImpl implements RegistryMetrics {
  public static final String METRIC_NAME_TIMINGS = "factcast.registry.duration";

  public static final String METRIC_NAME_COUNTS = "factcast.registry.meter";

  public static final String TAG_NAME_KEY = "name";

  private final MeterRegistry meterRegistry;

  public RegistryMetricsImpl(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
    /*
     * Register all non-exceptional meters, so that an operational dashboard
     * can visualize all possible operations dynamically without hardcoding
     * them.
     */
    for (RegistryMetrics.OP op : RegistryMetrics.OP.values()) {
      timer(op, null);
    }
    for (RegistryMetrics.EVENT e : RegistryMetrics.EVENT.values()) {
      count(e);
    }
  }

  private Counter counter(@NonNull EVENT op, Tags tags) {
    val t = Tags.of(Tag.of(TAG_NAME_KEY, op.event())).and(tags);

    return meterRegistry.counter(METRIC_NAME_COUNTS, t);
  }

  private Timer timer(@NonNull OP op, Tags tags) {
    val t = Tags.of(Tag.of(TAG_NAME_KEY, op.op())).and(tags);

    return meterRegistry.timer(METRIC_NAME_TIMINGS, t);
  }

  @Override
  public void timed(@NonNull OP operation, Tags tags, @NonNull Runnable fn) {
    timer(operation, tags).record(fn);
  }

  @Override
  public void timed(@NonNull OP operation, @NonNull Runnable fn) {
    timed(operation, null, fn);
  }

  @Override
  public <E extends Exception> void timed(
      @NonNull OP operation, @NonNull Class<E> exceptionClass, @NonNull RunnableWithException<E> fn)
      throws E {
    timed(operation, exceptionClass, null, fn);
  }

  @Override
  public <E extends Exception> void timed(
      @NonNull OP operation,
      @NonNull Class<E> exceptionClass,
      Tags tags,
      @NonNull RunnableWithException<E> fn)
      throws E {
    timed(
        operation,
        exceptionClass,
        tags,
        () -> {
          fn.run();

          return null;
        });
  }

  @Override
  public <T> T timed(@NonNull OP operation, Tags tags, @NonNull Supplier<T> fn) {
    return timer(operation, tags).record(fn);
  }

  @Override
  public <T> T timed(@NonNull OP operation, @NonNull Supplier<T> fn) {
    return timed(operation, null, fn);
  }

  @Override
  public <R, E extends Exception> R timed(
      @NonNull OP operation,
      @NonNull Class<E> exceptionClass,
      Tags tags,
      @NonNull SupplierWithException<R, E> fn)
      throws E {
    val timer = timer(operation, tags);
    val sw = Stopwatch.createStarted();

    try {
      return fn.get();
    } finally {
      sw.stop();
      timer.record(sw.elapsed());
    }
  }

  @Override
  public <R, E extends Exception> R timed(
      @NonNull OP operation,
      @NonNull Class<E> exceptionClass,
      @NonNull SupplierWithException<R, E> fn)
      throws E {
    return timed(operation, exceptionClass, null, fn);
  }

  @Override
  public void count(@NonNull EVENT event, Tags tags) {
    counter(event, tags).increment();
  }

  @Override
  public void count(@NonNull EVENT event) {
    count(event, null);
  }
}
