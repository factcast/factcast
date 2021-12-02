package org.factcast.server.grpc.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.function.Supplier;
import lombok.NonNull;
import org.factcast.factus.metrics.RunnableWithException;
import org.factcast.factus.metrics.SupplierWithException;
import org.factcast.server.grpc.metrics.ServerMetrics.EVENT;
import org.factcast.server.grpc.metrics.ServerMetrics.OP;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ServerMetricsImplTest {
  @Mock(lenient = true)
  MeterRegistry meterRegistry;

  @Mock
  Counter counter;

  @Mock
  Timer timer;

  @InjectMocks ServerMetricsImpl uut;

  @BeforeEach
  void setUp() {
    when(meterRegistry.timer(eq(ServerMetricsImpl.METRIC_NAME_TIMINGS), any(io.micrometer.core.instrument.Tags.class)))
            .thenReturn(timer);
    when(meterRegistry.counter(eq(ServerMetricsImpl.METRIC_NAME_COUNTS), any(io.micrometer.core.instrument.Tags.class)))
            .thenReturn(counter);
  }

  @Test
  void testTimerCreation() {
    uut.timed(OP.HANDSHAKE, () -> {});

    verify(meterRegistry)
            .timer(
                    ServerMetricsImpl.METRIC_NAME_TIMINGS,
                    io.micrometer.core.instrument.Tags.of(
                            io.micrometer.core.instrument.Tag.of(
                                    ServerMetricsImpl.TAG_NAME_KEY,
                                    OP.HANDSHAKE.op())));
  }

  @Test
  void testCounterCreation() {
    uut.count(EVENT.SOME_EVENT_CHANGE_ME);

    verify(meterRegistry)
            .counter(
                    ServerMetricsImpl.METRIC_NAME_COUNTS,
                    io.micrometer.core.instrument.Tags.of(
                            io.micrometer.core.instrument.Tag.of(
                                    ServerMetricsImpl.TAG_NAME_KEY,
                                    ServerMetrics.EVENT.SOME_EVENT_CHANGE_ME.event())));
  }

  @Test
  void testTimerRunnable() {
    uut.timed(OP.HANDSHAKE, () -> {});

    verify(timer).record(any(Runnable.class));
  }

  @Test
  void testTimerRunnableAndTags() {
    var customTag = io.micrometer.core.instrument.Tag.of("foo", "bar");
    uut.timed(OP.HANDSHAKE, io.micrometer.core.instrument.Tags.of(customTag), () -> {});

    verify(timer).record(any(Runnable.class));
    verify(meterRegistry)
            .timer(
                    ServerMetricsImpl.METRIC_NAME_TIMINGS,
                    io.micrometer.core.instrument.Tags.of(
                            customTag,
                            io.micrometer.core.instrument.Tag.of(
                                    ServerMetricsImpl.TAG_NAME_KEY,
                                    OP.HANDSHAKE.op())));
  }

  @Test
  void testTimerRunnableWithExceptions() {
    uut.timed(
            OP.HANDSHAKE, IllegalArgumentException.class, () -> {});

    verify(timer).record(any(Duration.class));
  }

  @Test
  void testTimerRunnableWithExceptionsAndTags() {
    var customTag = io.micrometer.core.instrument.Tag.of("foo", "bar");

    uut.timed(
            OP.HANDSHAKE,
            IllegalArgumentException.class,
            io.micrometer.core.instrument.Tags.of(customTag),
            () -> {});

    verify(timer).record(any(Duration.class));
    verify(meterRegistry)
            .timer(
                    ServerMetricsImpl.METRIC_NAME_TIMINGS,
                    io.micrometer.core.instrument.Tags.of(
                            customTag,
                            io.micrometer.core.instrument.Tag.of(
                                    ServerMetricsImpl.TAG_NAME_KEY,
                                    OP.HANDSHAKE.op())));
  }

  @Test
  void testTimerSupplier() {
    var customTag = io.micrometer.core.instrument.Tag.of("foo", "bar");

    when(timer.record(any(Supplier.class)))
            .thenAnswer(invocation -> invocation.getArgument(0, Supplier.class).get());

    var result =
            uut.timed(OP.HANDSHAKE, io.micrometer.core.instrument.Tags.of(customTag), () -> 5);

    assertEquals(5, result);

    verify(timer).record(any(Supplier.class));
    verify(meterRegistry)
            .timer(
                    ServerMetricsImpl.METRIC_NAME_TIMINGS,
                    io.micrometer.core.instrument.Tags.of(
                            customTag,
                            io.micrometer.core.instrument.Tag.of(
                                    ServerMetricsImpl.TAG_NAME_KEY,
                                    OP.HANDSHAKE.op())));
  }

  @Test
  void testTimerSupplierWithException() {
    var result =
            uut.timed(
                    OP.HANDSHAKE,
                    IllegalArgumentException.class,
                    () -> 5);

    assertEquals(5, result);

    verify(timer).record(any(Duration.class));
  }

  @Test
  void testTimerSupplierWithTags() {

    when(timer.record(any(Supplier.class)))
            .thenAnswer(invocation -> invocation.getArgument(0, Supplier.class).get());

    var result = uut.timed(OP.HANDSHAKE, () -> 5);

    assertEquals(5, result);

    verify(timer).record(any(Supplier.class));
  }

  @Test
  void testCounter() {
    uut.count(EVENT.SOME_EVENT_CHANGE_ME);

    verify(counter).increment();
  }

  @Test
  void testCounterWithTags() {
    var customTag = io.micrometer.core.instrument.Tag.of("foo", "bar");

    uut.count(EVENT.SOME_EVENT_CHANGE_ME, io.micrometer.core.instrument.Tags.of(customTag));

    verify(counter).increment();
    verify(meterRegistry)
            .counter(
                    ServerMetricsImpl.METRIC_NAME_COUNTS,
                    io.micrometer.core.instrument.Tags.of(
                            customTag,
                            Tag.of(
                                    ServerMetricsImpl.TAG_NAME_KEY,
                                    EVENT.SOME_EVENT_CHANGE_ME.event())));
  }
}
