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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import java.time.Duration;
import java.util.function.Supplier;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
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
    uut.timed(RegistryMetrics.OP.COMPACT_TRANSFORMATION_CACHE, () -> {});

    verify(meterRegistry)
        .timer(
            RegistryMetricsImpl.METRIC_NAME_TIMINGS,
            Tags.of(
                Tag.of(
                    RegistryMetricsImpl.TAG_NAME_KEY,
                    RegistryMetrics.OP.COMPACT_TRANSFORMATION_CACHE.op())));
  }

  @Test
  void testCounterCreation() {
    uut.count(RegistryMetrics.EVENT.SCHEMA_REGISTRY_UNAVAILABLE);

    verify(meterRegistry)
        .counter(
            RegistryMetricsImpl.METRIC_NAME_COUNTS,
            Tags.of(
                Tag.of(
                    RegistryMetricsImpl.TAG_NAME_KEY,
                    RegistryMetrics.EVENT.SCHEMA_REGISTRY_UNAVAILABLE.event())));
  }

  @Test
  void testTimerRunnable() {
    uut.timed(RegistryMetrics.OP.COMPACT_TRANSFORMATION_CACHE, () -> {});

    verify(timer).record(any(Runnable.class));
  }

  @Test
  void testTimerRunnableAndTags() {
    var customTag = Tag.of("foo", "bar");
    uut.timed(RegistryMetrics.OP.COMPACT_TRANSFORMATION_CACHE, Tags.of(customTag), () -> {});

    verify(timer).record(any(Runnable.class));
    verify(meterRegistry)
        .timer(
            RegistryMetricsImpl.METRIC_NAME_TIMINGS,
            Tags.of(
                customTag,
                Tag.of(
                    RegistryMetricsImpl.TAG_NAME_KEY,
                    RegistryMetrics.OP.COMPACT_TRANSFORMATION_CACHE.op())));
  }

  @Test
  void testTimerRunnableWithExceptions() {
    uut.timed(
        RegistryMetrics.OP.COMPACT_TRANSFORMATION_CACHE, IllegalArgumentException.class, () -> {});

    verify(timer).record(any(Duration.class));
  }

  @Test
  void testTimerRunnableWithExceptionsAndTags() {
    var customTag = Tag.of("foo", "bar");

    uut.timed(
        RegistryMetrics.OP.COMPACT_TRANSFORMATION_CACHE,
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
                    RegistryMetrics.OP.COMPACT_TRANSFORMATION_CACHE.op())));
  }

  @Test
  void testTimerSupplier() {
    var customTag = Tag.of("foo", "bar");

    when(timer.record(any(Supplier.class)))
        .thenAnswer(invocation -> invocation.getArgument(0, Supplier.class).get());

    var result =
        uut.timed(RegistryMetrics.OP.COMPACT_TRANSFORMATION_CACHE, Tags.of(customTag), () -> 5);

    assertEquals(5, result);

    verify(timer).record(any(Supplier.class));
    verify(meterRegistry)
        .timer(
            RegistryMetricsImpl.METRIC_NAME_TIMINGS,
            Tags.of(
                customTag,
                Tag.of(
                    RegistryMetricsImpl.TAG_NAME_KEY,
                    RegistryMetrics.OP.COMPACT_TRANSFORMATION_CACHE.op())));
  }

  @Test
  void testTimerSupplierWithException() {
    var result =
        uut.timed(
            RegistryMetrics.OP.COMPACT_TRANSFORMATION_CACHE,
            IllegalArgumentException.class,
            () -> 5);

    assertEquals(5, result);

    verify(timer).record(any(Duration.class));
  }

  @Test
  void testTimerSupplierWithTags() {

    when(timer.record(any(Supplier.class)))
        .thenAnswer(invocation -> invocation.getArgument(0, Supplier.class).get());

    var result = uut.timed(RegistryMetrics.OP.COMPACT_TRANSFORMATION_CACHE, () -> 5);

    assertEquals(5, result);

    verify(timer).record(any(Supplier.class));
  }

  @Test
  void testCounter() {
    uut.count(RegistryMetrics.EVENT.MISSING_TRANSFORMATION_INFO);

    verify(counter).increment();
  }

  @Test
  void testCounterWithTags() {
    var customTag = Tag.of("foo", "bar");

    uut.count(RegistryMetrics.EVENT.MISSING_TRANSFORMATION_INFO, Tags.of(customTag));

    verify(counter).increment();
    verify(meterRegistry)
        .counter(
            RegistryMetricsImpl.METRIC_NAME_COUNTS,
            Tags.of(
                customTag,
                Tag.of(
                    RegistryMetricsImpl.TAG_NAME_KEY,
                    RegistryMetrics.EVENT.MISSING_TRANSFORMATION_INFO.event())));
  }
}
