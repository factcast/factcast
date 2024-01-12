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
package org.factcast.client.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.io.IOException;
import java.time.Duration;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import lombok.NonNull;
import lombok.SneakyThrows;
import org.factcast.client.grpc.FactCastGrpcClientProperties.ResilienceConfiguration;
import org.factcast.client.grpc.ResilientGrpcSubscription.DelegatingFactObserver;
import org.factcast.client.grpc.ResilientGrpcSubscription.SubscriptionHolder;
import org.factcast.client.grpc.ResilientGrpcSubscription.ThrowingBiConsumer;
import org.factcast.core.Fact;
import org.factcast.core.FactStreamPosition;
import org.factcast.core.TestFactStreamPosition;
import org.factcast.core.store.RetryableException;
import org.factcast.core.subscription.FactStreamInfo;
import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.SubscriptionClosedException;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.core.subscription.observer.FactObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ResilientGrpcSubscriptionTest {
  @Mock(lenient = true)
  private GrpcFactStore store;

  @Mock private SubscriptionRequestTO req;

  @Mock private FactObserver obs;

  @Mock private Subscription subscription;

  private final ArgumentCaptor<FactObserver> observerAC =
      ArgumentCaptor.forClass(FactObserver.class);

  ResilienceConfiguration config = new ResilienceConfiguration();
  ResilientGrpcSubscription uut;

  @BeforeEach
  public void setup() {
    // order is important here, you have been warned
    when(store.internalSubscribe(any(), observerAC.capture())).thenReturn(subscription);
    uut = spy(new ResilientGrpcSubscription(store, req, obs, config));
    when(store.subscribe(any(), observerAC.capture())).thenReturn(uut);
  }

  @SneakyThrows
  @Test
  void testClosesOnlyOnce() {
    uut.close();
    Mockito.verify(subscription).close();
    uut.close();
    Mockito.verifyNoMoreInteractions(subscription);
  }

  @Test
  void testAwaitCompleteDelegatesToSubscription() {
    // needs to return immediately
    uut.awaitComplete();
    verify(subscription).awaitComplete();
  }

  @Test
  void testAwaitCatchupDelegatesToSubscription() {
    // needs to return immediately
    uut.awaitCatchup();
    verify(subscription).awaitCatchup();
  }

  @Test
  void testAwaitCompleteLong() throws Exception {
    when(subscription.awaitComplete(anyLong()))
        .thenThrow(TimeoutException.class)
        .then(x -> subscription);

    assertThrows(TimeoutException.class, () -> uut.awaitComplete(551));

    assertTimeout(
        Duration.ofMillis(1000),
        () -> {
          assertThat(uut.awaitComplete(552)).isSameAs(uut);
        });
    // await call was passed
    verify(subscription).awaitComplete(552);
  }

  @Test
  void testAwaitCatchupLong() throws Exception {
    when(subscription.awaitCatchup(anyLong()))
        .thenThrow(TimeoutException.class)
        .then(x -> subscription);

    assertThrows(TimeoutException.class, () -> uut.awaitCatchup(551));

    assertTimeout(
        Duration.ofMillis(1000),
        () -> {
          assertThat(uut.awaitCatchup(552)).isSameAs(uut);
        });
    // await call was passed
    verify(subscription).awaitCatchup(552);
  }

  @Test
  void testAssertSubscriptionStateNotClosed() throws Exception {
    uut.close();
    assertThrows(SubscriptionClosedException.class, () -> uut.awaitCatchup());
    assertThrows(SubscriptionClosedException.class, () -> uut.awaitCatchup(1L));
    assertThrows(SubscriptionClosedException.class, () -> uut.awaitComplete());
    assertThrows(SubscriptionClosedException.class, () -> uut.awaitComplete(1L));
  }

  @Test
  void isServerException() throws Exception {

    assertThat(ClientExceptionHelper.isRetryable(new RuntimeException())).isFalse();
    assertThat(ClientExceptionHelper.isRetryable(new IllegalArgumentException())).isFalse();
    assertThat(ClientExceptionHelper.isRetryable(new IOException())).isFalse();
    assertThat(
            ClientExceptionHelper.isRetryable(new StatusRuntimeException(Status.UNAUTHENTICATED)))
        .isFalse();
    assertThat(
            ClientExceptionHelper.isRetryable(new StatusRuntimeException(Status.PERMISSION_DENIED)))
        .isFalse();
    assertThat(
            ClientExceptionHelper.isRetryable(
                new StatusRuntimeException(Status.RESOURCE_EXHAUSTED)))
        .isFalse();
    assertThat(
            ClientExceptionHelper.isRetryable(new StatusRuntimeException(Status.INVALID_ARGUMENT)))
        .isFalse();

    //
    assertThat(ClientExceptionHelper.isRetryable(new StatusRuntimeException(Status.UNKNOWN)))
        .isTrue();
    assertThat(ClientExceptionHelper.isRetryable(new StatusRuntimeException(Status.UNAVAILABLE)))
        .isTrue();
    assertThat(ClientExceptionHelper.isRetryable(new StatusRuntimeException(Status.ABORTED)))
        .isTrue();

    // assertThat(uut.isNotRetryable(new TransformationExceptione());
    // assertThat(uut.isNotRetryable(new MissingTransformationInformationException());
    // important because it needs to reconnect, which only happens if it is NOT categorized as
    // serverException
    // assertThat(uut.isNotRetryable(new StaleSubscriptionDetectedException())).isFalse();
  }

  @Test
  void deletegateWithTimeout() {

    config.setEnabled(true).setAttempts(100);

    ThrowingBiConsumer<Subscription, Long> consumer =
        (s, l) -> {
          sleep(300);
          throw new RetryableException(new Exception());
        };
    assertThatThrownBy(
            () -> {
              uut.delegate(consumer, 1000);
            })
        .isInstanceOf(TimeoutException.class);
  }

  @Test
  void deletegateThrowing() {
    config.setEnabled(true).setAttempts(100);

    Consumer<Subscription> consumer = mock(Consumer.class);
    doThrow(
            new RetryableException(new IOException()),
            new RetryableException(new Exception()),
            new IllegalArgumentException())
        .when(consumer)
        .accept(any());

    assertThatThrownBy(
            () -> {
              uut.delegate(consumer);
            })
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void deletegateThrowingWithRetryDisabled() {
    config.setEnabled(false);

    Consumer<Subscription> consumer = mock(Consumer.class);
    RetryableException initial = new RetryableException(new IOException());
    doThrow(initial, new RetryableException(new Exception()), new IllegalArgumentException())
        .when(consumer)
        .accept(any());

    assertThatThrownBy(
            () -> {
              uut.delegate(consumer);
            })
        .isSameAs(initial);
  }

  @Test
  void testFail() {
    IOException ex = new IOException();
    assertThatThrownBy(
            () -> {
              uut.fail(ex);
            })
        .isInstanceOf(RuntimeException.class)
        .getCause()
        .isInstanceOf(IOException.class);

    verify(obs).onError(ex);
  }

  @SneakyThrows
  private void sleep(int i) {
    Thread.sleep(i);
  }

  @Nested
  class DelegatingFactObserverTest {

    DelegatingFactObserver dfo;

    @BeforeEach
    public void setup() {
      // order is important here, you have been warned
      when(store.internalSubscribe(any(), observerAC.capture())).thenReturn(subscription);
      uut = spy(new ResilientGrpcSubscription(store, req, obs, config));
      when(store.subscribe(any(), observerAC.capture())).thenReturn(uut);
      dfo = uut.new DelegatingFactObserver();
    }

    @Test
    void catchupDelegates() {
      dfo.onCatchup();
      verify(obs).onCatchup();
    }

    @Test
    void nextDelegates() {
      @NonNull Fact f = Fact.builder().ns("foo").type("bar").buildWithoutPayload();
      dfo.onNext(f);
      verify(obs).onNext(f);
    }

    @Test
    void nextChecksForClosing() {
      uut.close();
      @NonNull Fact f = Fact.builder().ns("foo").type("bar").buildWithoutPayload();
      dfo.onNext(f);
      verify(obs, never()).onNext(f);
    }

    @Test
    void completeDelegates() {
      dfo.onComplete();
      verify(obs).onComplete();
    }

    @Test
    void ffwDelegates() {
      FactStreamPosition pos = TestFactStreamPosition.random();
      dfo.onFastForward(pos);
      verify(obs).onFastForward(pos);
    }

    @Test
    void infoDelegates() {
      @NonNull FactStreamInfo info = new FactStreamInfo(1, 10);
      dfo.onFactStreamInfo(info);
      verify(obs).onFactStreamInfo(info);
    }

    @SneakyThrows
    @Test
    void onErrorFailing() {

      try {
        dfo.onError(new IOException());
      } catch (Exception ignore) {
      }

      verify(subscription).close();
      assertThat(uut.resilience().numberOfAttemptsInWindow()).isEqualTo(1);
    }

    @SneakyThrows
    @Test
    void onErrorReconnecting() {

      doNothing().when(uut).reConnect();

      dfo.onError(new RetryableException(new IOException()));

      verify(subscription).close();
      verify(uut).reConnect();
      assertThat(uut.resilience().numberOfAttemptsInWindow()).isEqualTo(1);
    }
  }

  @Nested
  class SubscriptionHolderTest {
    private SubscriptionHolder sh;

    @BeforeEach
    public void setup() {
      // order is important here, you have been warned
      when(store.internalSubscribe(any(), observerAC.capture())).thenReturn(subscription);
      uut = new ResilientGrpcSubscription(store, req, obs, config);
      when(store.subscribe(any(), observerAC.capture())).thenReturn(uut);
      sh = uut.new SubscriptionHolder();
    }

    @SneakyThrows
    @Test
    void blocksUntilSubscriptionAvailable() {
      Subscription s = mock(Subscription.class);
      new Timer()
          .schedule(
              new TimerTask() {
                @Override
                public void run() {
                  sh.set(s);
                }
              },
              1000);
      assertThat(sh.getAndBlock(0)).isSameAs(s);
    }

    @Test
    void blocksUntilTimeroutReached() {
      assertThatThrownBy(
              () -> {
                sh.getAndBlock(100);
              })
          .isInstanceOf(TimeoutException.class);
    }
  }
}
