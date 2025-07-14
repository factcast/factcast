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
package org.factcast.factus;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.function.IntToLongFunction;
import lombok.NonNull;
import lombok.SneakyThrows;
import nl.altindag.log.LogCaptor;
import org.factcast.core.Fact;
import org.factcast.core.FactStreamPosition;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.store.FactStore;
import org.factcast.core.subscription.Subscription;
import org.factcast.factus.batch.PublishBatch;
import org.factcast.factus.event.EventObject;
import org.factcast.factus.lock.Locked;
import org.factcast.factus.lock.LockedOnSpecs;
import org.factcast.factus.projection.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FactusTest {

  @Mock FactStore mockedStore;

  private final Factus underTest =
      spy(
          new Factus() {
            @Override
            public <T> T publish(@NonNull EventObject e, @NonNull Function<Fact, T> resultFn) {
              return null;
            }

            @Override
            public <T> T publish(
                @NonNull List<EventObject> e, @NonNull Function<List<Fact>, T> resultFn) {
              return null;
            }

            @Override
            public @NonNull PublishBatch batch() {
              return null;
            }

            @Override
            public <P extends SubscribedProjection> Subscription subscribeAndBlock(
                @NonNull P subscribedProjection) {
              return null;
            }

            @Override
            public <P extends SubscribedProjection> Subscription subscribeAndBlock(
                @NonNull P subscribedProjection, @NonNull Duration retryWaitTime) {
              return null;
            }

            @Override
            public <A extends Aggregate> Locked<A> withLockOn(
                @NonNull Class<A> aggregateClass, UUID id) {
              return null;
            }

            @Override
            public <P extends SnapshotProjection> Locked<P> withLockOn(
                @NonNull Class<P> snapshotClass) {
              return null;
            }

            @Override
            public <M extends ManagedProjection> Locked<M> withLockOn(@NonNull M managed) {
              return null;
            }

            @Override
            public Fact toFact(@NonNull EventObject e) {
              return null;
            }

            @Override
            public LockedOnSpecs withLockOn(@NonNull FactSpec spec, FactSpec... additional) {
              return null;
            }

            @Override
            public LockedOnSpecs withLockOn(@NonNull List<FactSpec> specs) {
              return null;
            }

            @Override
            public OptionalLong serialOf(@NonNull UUID factId) {
              return OptionalLong.empty();
            }

            @Override
            public void close() throws IOException {}

            @Override
            public <P extends SnapshotProjection> @NonNull P fetch(
                @NonNull Class<P> projectionClass) {
              return null;
            }

            @Override
            public @NonNull <A extends Aggregate> Optional<A> find(
                @NonNull Class<A> aggregateClass, @NonNull UUID aggregateId) {
              return Optional.empty();
            }

            @Override
            public <P extends ManagedProjection> void update(
                @NonNull P managedProjection, @NonNull Duration maxWaitTime)
                throws TimeoutException {}

            @Override
            public void publish(@NonNull Fact f) {}

            @Override
            public FactStore store() {
              return mockedStore;
            }
          });

  @Test
  void defaultMethod_publishListEventObjects() {
    List<EventObject> l = Lists.newArrayList(new EP(), new EP());
    underTest.publish(l);

    verify(underTest).publish(same(l), any());
  }

  @Test
  void defaultMethod_voidSubscribe() {
    SubscribedProjection p = mock(SubscribedProjection.class);
    underTest.subscribe(p);

    sleep(); // call is async, so we wait a bit

    verify(underTest).subscribeAndBlock(same(p));
  }

  @Test
  void defaultMethod_voidSubscribe_catchesFactusClosedException() {
    try (LogCaptor logCaptor = LogCaptor.forClass(Factus.class)) {
      SubscribedProjection p = mock(SubscribedProjection.class);
      doThrow(new FactusClosedException("closed")).when(underTest).subscribeAndBlock(p);
      underTest.subscribe(p);

      sleep(); // call is async, so we wait a bit

      assertThat(logCaptor.getInfoLogs())
          .contains("Aborting subscription " + p.getClass() + ": closed");
      assertThat(logCaptor.getErrorLogs()).isEmpty();
    }
  }

  @Test
  void defaultMethod_voidSubscribe_catchesException() {
    try (LogCaptor logCaptor = LogCaptor.forClass(Factus.class)) {
      SubscribedProjection p = mock(SubscribedProjection.class);
      doThrow(new RuntimeException("test")).when(underTest).subscribeAndBlock(p);
      underTest.subscribe(p);

      sleep(); // call is async, so we wait a bit

      assertThat(logCaptor.getErrorLogs()).contains("Error subscribing to " + p.getClass());
    }
  }

  static class SP implements SnapshotProjection {}

  @Test
  void defaultMethod_withLockOnInstance() {
    SP p = new SP();
    underTest.withLockOn(p);
    verify(underTest).withLockOn(SP.class);
  }

  @SneakyThrows
  private void sleep() {
    Thread.sleep(100);
  }

  static class EP implements EventObject {
    @Override
    public Set<UUID> aggregateIds() {
      return Sets.newHashSet();
    }
  }

  @Nested
  class WhenWaitingForFact {

    private final Duration defaultTimeout = Duration.ofMillis(100);
    private final FactStreamPosition factStreamPositionMock =
        Mockito.mock(FactStreamPosition.class);
    private final SubscribedProjection subscribedProjectionMock =
        Mockito.mock(SubscribedProjection.class);

    @Test
    void returns() {
      UUID factId = UUID.randomUUID();
      when(underTest.serialOf(factId)).thenReturn(OptionalLong.of(2));
      when(subscribedProjectionMock.factStreamPosition()).thenReturn(factStreamPositionMock);
      when(factStreamPositionMock.serial()).thenReturn(2L);

      assertThatNoException()
          .isThrownBy(() -> underTest.waitFor(subscribedProjectionMock, factId, defaultTimeout));
      verify(subscribedProjectionMock, times(1)).factStreamPosition();
    }

    @Test
    void timesOutOnFactNotConsumed() {
      UUID factId = UUID.randomUUID();
      when(underTest.serialOf(factId)).thenReturn(OptionalLong.of(2));
      when(subscribedProjectionMock.factStreamPosition()).thenReturn(factStreamPositionMock);
      when(factStreamPositionMock.serial()).thenReturn(1L);

      assertThatThrownBy(() -> underTest.waitFor(subscribedProjectionMock, factId, defaultTimeout))
          .isInstanceOf(TimeoutException.class);
      verify(subscribedProjectionMock, atLeast(2)).factStreamPosition();
    }

    @Test
    void failsEarlyForFactsWithoutSerial() {
      UUID factId = UUID.randomUUID();
      when(underTest.serialOf(factId)).thenReturn(OptionalLong.empty());
      when(subscribedProjectionMock.factStreamPosition()).thenReturn(factStreamPositionMock);

      assertThatThrownBy(() -> underTest.waitFor(subscribedProjectionMock, factId, defaultTimeout))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage(
              "Fact with id " + factId + " not found. Make sure to publish before waiting for it.");
      verify(subscribedProjectionMock, never()).factStreamPosition();
    }

    @Test
    void timesOutOnNullFactStreamPosition() {
      UUID factId = UUID.randomUUID();
      when(underTest.serialOf(factId)).thenReturn(OptionalLong.of(2));
      when(subscribedProjectionMock.factStreamPosition()).thenReturn(factStreamPositionMock);

      assertThatThrownBy(() -> underTest.waitFor(subscribedProjectionMock, factId, defaultTimeout))
          .isInstanceOf(TimeoutException.class);
    }

    @Test
    void cachesSerials() throws Exception {
      UUID factId1 = UUID.randomUUID();
      UUID factId2 = UUID.randomUUID();
      when(underTest.serialOf(factId1)).thenReturn(OptionalLong.of(2));
      when(underTest.serialOf(factId2)).thenReturn(OptionalLong.of(4));
      when(subscribedProjectionMock.factStreamPosition()).thenReturn(factStreamPositionMock);
      when(factStreamPositionMock.serial()).thenReturn(5L);

      underTest.waitFor(subscribedProjectionMock, factId1, defaultTimeout);
      underTest.waitFor(subscribedProjectionMock, factId2, defaultTimeout);
      underTest.waitFor(subscribedProjectionMock, factId1, defaultTimeout);

      verify(underTest, times(1)).serialOf(factId1);
      verify(underTest, times(1)).serialOf(factId2);
    }

    @Test
    void retriesWithConstantBackoffStrategy() {
      UUID factId = UUID.randomUUID();
      when(underTest.serialOf(factId)).thenReturn(OptionalLong.of(2));
      when(subscribedProjectionMock.factStreamPosition()).thenReturn(factStreamPositionMock);
      when(factStreamPositionMock.serial()).thenReturn(1L);

      // constant backoff strategy
      IntToLongFunction backoffFunction = i -> 10L;
      assertThatThrownBy(
              () ->
                  underTest.waitFor(
                      subscribedProjectionMock, factId, defaultTimeout, backoffFunction))
          .isInstanceOf(TimeoutException.class);

      // this really depends on the timing of the test, so we can't assert the exact number
      // considering that the pipeline might be slower than the local environment
      verify(subscribedProjectionMock, atLeast(1)).factStreamPosition();
      verify(subscribedProjectionMock, atMost(11)).factStreamPosition();
    }

    @Test
    void retriesWithLinearBackoffStrategy() {
      UUID factId = UUID.randomUUID();
      when(underTest.serialOf(factId)).thenReturn(OptionalLong.of(2));
      when(subscribedProjectionMock.factStreamPosition()).thenReturn(factStreamPositionMock);
      when(factStreamPositionMock.serial()).thenReturn(1L);

      // linear backoff strategy
      IntToLongFunction backoffFunction = i -> i * 10L;
      assertThatThrownBy(
              () ->
                  underTest.waitFor(
                      subscribedProjectionMock, factId, defaultTimeout, backoffFunction))
          .isInstanceOf(TimeoutException.class);

      // this really depends on the timing of the test, so we can't assert the exact number
      // considering that the pipeline might be slower than the local environment
      verify(subscribedProjectionMock, atLeast(1)).factStreamPosition();
      verify(subscribedProjectionMock, atMost(6)).factStreamPosition();
    }

    @Test
    void retriesWithExponentialBackoffStrategy() {
      UUID factId = UUID.randomUUID();
      when(underTest.serialOf(factId)).thenReturn(OptionalLong.of(2));
      when(subscribedProjectionMock.factStreamPosition()).thenReturn(factStreamPositionMock);
      when(factStreamPositionMock.serial()).thenReturn(1L);

      // exponential backoff strategy
      IntToLongFunction backoffFunction = i -> (long) Math.pow(10, i);
      assertThatThrownBy(
              () ->
                  underTest.waitFor(
                      subscribedProjectionMock, factId, defaultTimeout, backoffFunction))
          .isInstanceOf(TimeoutException.class);

      // this really depends on the timing of the test, so we can't assert the exact number
      // considering that the pipeline might be slower than the local environment
      verify(subscribedProjectionMock, atLeast(1)).factStreamPosition();
      verify(subscribedProjectionMock, atMost(3)).factStreamPosition();
    }
  }

  @Nested
  class ClockTests {
    @Test
    void testEpochSeconds() {
      // INIT
      long epochMilli = 1_234;
      Instant now = Instant.ofEpochMilli(epochMilli);
      when(mockedStore.currentTime()).thenReturn(epochMilli);

      // RUN / ASSERT
      assertThat(underTest.currentTime()).isEqualTo(now);
    }
  }
}
