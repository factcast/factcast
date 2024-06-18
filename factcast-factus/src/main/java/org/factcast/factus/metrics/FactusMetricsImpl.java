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

import static org.factcast.factus.metrics.TagKeys.TAG_NAME;

import com.google.common.base.Stopwatch;
import io.micrometer.core.instrument.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import lombok.NonNull;
import org.factcast.core.util.RunnableWithException;
import org.factcast.core.util.SupplierWithException;

public class FactusMetricsImpl implements FactusMetrics {
  public static final String METRIC_NAME_TIMINGS = "factus.timings";

  public static final String METRIC_NAME_COUNTS = "factus.counts";

  public static final String METRIC_NAME_GAUGES = "factus.gauges";

  private final MeterRegistry meterRegistry;

  public FactusMetricsImpl(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
    meterRegistry.counter(METRIC_NAME_COUNTS);
    meterRegistry.timer(METRIC_NAME_TIMINGS);
  }

  private Counter counter(@NonNull CountedEvent op, Tags tags) {
    Tags t = Tags.of(Tag.of(TAG_NAME, op.event())).and(tags);

    return meterRegistry.counter(METRIC_NAME_COUNTS, t);
  }

  private Timer timer(@NonNull TimedOperation op, Tags tags) {
    Tags t = Tags.of(Tag.of(TAG_NAME, op.op())).and(tags);

    return meterRegistry.timer(METRIC_NAME_TIMINGS, t);
  }

  private AtomicLong gauge(@NonNull GaugedEvent op, Tags tags) {
    Tags t = Tags.of(Tag.of(TAG_NAME, op.event())).and(tags);
    return meterRegistry.gauge(METRIC_NAME_GAUGES, t, new AtomicLong(0));
  }

  @Override
  public void timed(@NonNull TimedOperation operation, Tags tags, @NonNull Runnable fn) {
    timer(operation, tags).record(fn);
  }

  @Override
  public <E extends Exception> void timed(
      @NonNull TimedOperation operation,
      @NonNull Class<E> exceptionClass,
      @NonNull RunnableWithException<E> fn)
      throws E {
    timed(operation, exceptionClass, null, fn);
  }

  @Override
  public <E extends Exception> void timed(
      @NonNull TimedOperation operation,
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
  public <T> T timed(@NonNull TimedOperation operation, Tags tags, @NonNull Supplier<T> fn) {
    return timer(operation, tags).record(fn);
  }

  @Override
  public <R, E extends Exception> R timed(
      @NonNull TimedOperation operation,
      @NonNull Class<E> exceptionClass,
      Tags tags,
      @NonNull SupplierWithException<R, E> fn)
      throws E {
    Timer timer = timer(operation, tags);
    Stopwatch sw = Stopwatch.createStarted();

    try {
      return fn.get();
    } finally {
      sw.stop();
      timer.record(sw.elapsed());
    }
  }

  @Override
  public void timed(TimedOperation operation, Tags tags, long milliseconds) {
    Timer timer = timer(operation, tags);
    timer.record(milliseconds, TimeUnit.MILLISECONDS);
  }

  @Override
  public void count(@NonNull CountedEvent event, Tags tags) {
    counter(event, tags).increment();
  }

  @Override
  public void record(@NonNull GaugedEvent event, Tags tags, long value) {
    AtomicLong g = gauge(event, tags);
    g.set(value);
  }
}
