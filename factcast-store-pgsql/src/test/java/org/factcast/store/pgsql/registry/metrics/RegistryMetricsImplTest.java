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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.micrometer.core.instrument.*;
import java.time.Duration;
import java.util.function.Supplier;
import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RegistryMetricsImplTest {
  @Mock(lenient = true)
  MeterRegistry meterRegistry;

  @Mock Counter counter;

  @Mock Timer timer;

  @InjectMocks RegistryMetricsImpl uut;

  @BeforeEach
  void setUp() {
    when(meterRegistry.timer(eq(RegistryMetricsImpl.METRIC_NAME_TIMINGS), any(Tags.class)))
        .thenReturn(timer);
    when(meterRegistry.counter(eq(RegistryMetricsImpl.METRIC_NAME_COUNTS), any(Tags.class)))
        .thenReturn(counter);
  }

  @Test
  void testTimerCreation() {
    uut.timed(TimedOperation.COMPACT_TRANSFORMATION_CACHE, () -> {});

    verify(meterRegistry)
        .timer(
            RegistryMetricsImpl.METRIC_NAME_TIMINGS,
            Tags.of(
                Tag.of(
                    RegistryMetricsImpl.TAG_NAME_KEY,
                    TimedOperation.COMPACT_TRANSFORMATION_CACHE.op())));
  }

  @Test
  void testCounterCreation() {
    uut.count(MetricEvent.SCHEMA_REGISTRY_UNAVAILABLE);

    verify(meterRegistry)
        .counter(
            RegistryMetricsImpl.METRIC_NAME_COUNTS,
            Tags.of(
                Tag.of(
                    RegistryMetricsImpl.TAG_NAME_KEY,
                    MetricEvent.SCHEMA_REGISTRY_UNAVAILABLE.event())));
  }

  @Test
  void testTimerRunnable() {
    uut.timed(TimedOperation.COMPACT_TRANSFORMATION_CACHE, () -> {});

    verify(timer).record(any(Runnable.class));
  }

  @Test
  void testTimerRunnableAndTags() {
    val customTag = Tag.of("foo", "bar");
    uut.timed(TimedOperation.COMPACT_TRANSFORMATION_CACHE, Tags.of(customTag), () -> {});

    verify(timer).record(any(Runnable.class));
    verify(meterRegistry)
        .timer(
            RegistryMetricsImpl.METRIC_NAME_TIMINGS,
            Tags.of(
                customTag,
                Tag.of(
                    RegistryMetricsImpl.TAG_NAME_KEY,
                    TimedOperation.COMPACT_TRANSFORMATION_CACHE.op())));
  }

  @Test
  void testTimerRunnableWithExceptions() {
    uut.timed(
        TimedOperation.COMPACT_TRANSFORMATION_CACHE, IllegalArgumentException.class, () -> {});

    verify(timer).record(any(Duration.class));
  }

  @Test
  void testTimerRunnableWithExceptionsAndTags() {
    val customTag = Tag.of("foo", "bar");

    uut.timed(
        TimedOperation.COMPACT_TRANSFORMATION_CACHE,
        IllegalArgumentException.class,
        Tags.of(customTag),
        () -> {});

    verify(timer).record(any(Duration.class));
    verify(meterRegistry)
        .timer(
            RegistryMetricsImpl.METRIC_NAME_TIMINGS,
            Tags.of(
                customTag,
                Tag.of(
                    RegistryMetricsImpl.TAG_NAME_KEY,
                    TimedOperation.COMPACT_TRANSFORMATION_CACHE.op())));
  }

  @Test
  void testTimerSupplier() {
    val customTag = Tag.of("foo", "bar");

    when(timer.record(any(Supplier.class)))
        .thenAnswer(invocation -> invocation.getArgument(0, Supplier.class).get());

    val result =
        uut.timed(TimedOperation.COMPACT_TRANSFORMATION_CACHE, Tags.of(customTag), () -> 5);

    assertEquals(5, result);

    verify(timer).record(any(Supplier.class));
    verify(meterRegistry)
        .timer(
            RegistryMetricsImpl.METRIC_NAME_TIMINGS,
            Tags.of(
                customTag,
                Tag.of(
                    RegistryMetricsImpl.TAG_NAME_KEY,
                    TimedOperation.COMPACT_TRANSFORMATION_CACHE.op())));
  }

  @Test
  void testTimerSupplierWithException() {
    val result =
        uut.timed(
            TimedOperation.COMPACT_TRANSFORMATION_CACHE, IllegalArgumentException.class, () -> 5);

    assertEquals(5, result);

    verify(timer).record(any(Duration.class));
  }

  @Test
  void testTimerSupplierWithTags() {

    when(timer.record(any(Supplier.class)))
        .thenAnswer(invocation -> invocation.getArgument(0, Supplier.class).get());

    val result = uut.timed(TimedOperation.COMPACT_TRANSFORMATION_CACHE, () -> 5);

    assertEquals(5, result);

    verify(timer).record(any(Supplier.class));
  }

  @Test
  void testCounter() {
    uut.count(MetricEvent.MISSING_TRANSFORMATION_INFO);

    verify(counter).increment();
  }

  @Test
  void testCounterWithTags() {
    val customTag = Tag.of("foo", "bar");

    uut.count(MetricEvent.MISSING_TRANSFORMATION_INFO, Tags.of(customTag));

    verify(counter).increment();
    verify(meterRegistry)
        .counter(
            RegistryMetricsImpl.METRIC_NAME_COUNTS,
            Tags.of(
                customTag,
                Tag.of(
                    RegistryMetricsImpl.TAG_NAME_KEY,
                    MetricEvent.MISSING_TRANSFORMATION_INFO.event())));
  }
}
