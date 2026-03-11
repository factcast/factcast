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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import lombok.NonNull;
import lombok.SneakyThrows;
import org.factcast.client.grpc.FactCastGrpcClientProperties.ResilienceConfiguration;
import org.factcast.client.grpc.ResilientGrpcSubscription.*;
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
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ResilientGrpcSubscriptionTest {
  @Mock(strictness = Mock.Strictness.LENIENT)
  private GrpcFactStore store;

  @Mock private SubscriptionRequestTO req;

  @Mock private FactObserver obs;

  @Mock private Subscription subscription;

  private final ArgumentCaptor<FactObserver> observerAC =
      ArgumentCaptor.forClass(FactObserver.class);

  final ResilienceConfiguration config = new ResilienceConfiguration();
  ResilientGrpcSubscription uut;

  @BeforeEach
  void setup() {
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

    final long deterministicNow = System.currentTimeMillis();
    try (MockedStatic<NowProvider> nowProvider = mockStatic(NowProvider.class)) {
      // to make sure we can have deterministic expectations on the waitTimeInMillis
      nowProvider.when(NowProvider::get).thenReturn(deterministicNow);

      assertThrows(TimeoutException.class, () -> uut.awaitComplete(551));
      assertThat(uut.awaitComplete(552)).isSameAs(uut);
      // await call was passed
      verify(subscription).awaitComplete(552);
    }
  }

  @Test
  void testAwaitCatchupLong() throws Exception {
    when(subscription.awaitCatchup(anyLong()))
        .thenThrow(TimeoutException.class)
        .then(x -> subscription);

    final long deterministicNow = System.currentTimeMillis();
    try (MockedStatic<NowProvider> nowProvider = mockStatic(NowProvider.class)) {
      // to make sure we can have deterministic expectations on the waitTimeInMillis
      nowProvider.when(NowProvider::get).thenReturn(deterministicNow);
      assertThrows(TimeoutException.class, () -> uut.awaitCatchup(551));

      assertThat(uut.awaitCatchup(552)).isSameAs(uut);
      // await call was passed
      verify(subscription).awaitCatchup(552);
    }
  }

  @Test
  void testAssertSubscriptionStateNotClosed() {
    uut.close();
    assertThrows(SubscriptionClosedException.class, () -> uut.awaitCatchup());
    assertThrows(SubscriptionClosedException.class, () -> uut.awaitCatchup(1L));
    assertThrows(SubscriptionClosedException.class, () -> uut.awaitComplete());
    assertThrows(SubscriptionClosedException.class, () -> uut.awaitComplete(1L));
  }

  @Test
  void isServerException() {

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
  }

  @Test
  void onCloseStacksUpAndIgnoresException() {

    class DoNothing implements Runnable {
      @Override
      public void run() {
        // do nothing
      }
    }

    class Fails implements Runnable {
      @Override
      public void run() {
        throw new IllegalArgumentException();
      }
    }

    Runnable h1 = spy(new DoNothing());
    Runnable h2 = spy(new DoNothing());
    Runnable h3 = spy(new Fails());
    Runnable h4 = spy(new DoNothing());

    uut.onClose(h1);
    uut.onClose(h2);
    uut.onClose(h3);
    uut.onClose(h4);

    uut.close();

    verify(h1).run();
    verify(h2).run();
    verify(h3).run();
    verify(h4).run();
  }

  @Test
  void delegateWithTimeout() {

    config.setEnabled(true).setAttempts(100);

    ThrowingBiConsumer<Subscription, Long> consumer =
        (s, l) -> {
          sleep(300);
          throw new RetryableException(new Exception());
        };
    assertThatThrownBy(() -> uut.delegate(consumer, 1000)).isInstanceOf(TimeoutException.class);
  }

  @Test
  void delegateThrowing() {
    config.setEnabled(true).setAttempts(100);

    Consumer<Subscription> consumer = mock(Consumer.class);
    doThrow(
            new RetryableException(new IOException()),
            new RetryableException(new Exception()),
            new IllegalArgumentException())
        .when(consumer)
        .accept(any());

    assertThatThrownBy(() -> uut.delegate(consumer)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void delegateThrowingWithRetryDisabled() {
    config.setEnabled(false);

    Consumer<Subscription> consumer = mock(Consumer.class);
    RetryableException initial = new RetryableException(new IOException());
    doThrow(initial, new RetryableException(new Exception()), new IllegalArgumentException())
        .when(consumer)
        .accept(any());

    assertThatThrownBy(() -> uut.delegate(consumer)).isSameAs(initial);
  }

  @Test
  void testFail() {
    IOException ex = new IOException();
    assertThatThrownBy(() -> uut.fail(ex))
        .isInstanceOf(RuntimeException.class)
        .cause()
        .isInstanceOf(IOException.class);

    verify(obs).onError(ex);
  }

  @Test
  void newResilientGrpcSubscriptionHasNoErrorCause() {
    assertThat(uut.onErrorCause().get()).isNull();
  }

  @SneakyThrows
  private void sleep(int i) {
    Thread.sleep(i);
  }

  @Nested
  class DelegatingFactObserverTest {

    DelegatingFactObserver dfo;

    @BeforeEach
    void setup() {
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
        // ignore
      }

      verify(subscription).close();
      assertThat(uut.resilience().numberOfAttemptsInWindow()).isEqualTo(1);
    }

    @Test
    void remembersOnErrorCause() {
      IOException exception = new IOException();

      try {
        dfo.onError(exception);
      } catch (Exception ignore) {
        // ignore
      }

      assertThat(uut.onErrorCause().get()).isSameAs(exception);
    }

    @SneakyThrows
    @Test
    void successfulReConnectWipesSavedOnErrorCause() {
      try {
        dfo.onError(new RetryableException(new IOException()));
      } catch (Exception ignore) {
        // ignore
      }

      verify(uut).reConnect();
      verify(uut).doConnect();
      assertThat(uut.onErrorCause().get()).isNull();
    }

    @Test
    void delegatingToInnerSubscriptionAfterOnErrorRethrowsCausingThrowable() {
      Consumer<Subscription> consumer = mock(Consumer.class);
      Throwable t = new RuntimeException();

      try {
        dfo.onError(t);
      } catch (Exception ignore) {
        // ignore
      }

      assertThatThrownBy(() -> uut.delegate(consumer)).isSameAs(t);
    }

    @SneakyThrows
    @Test
    void onErrorReconnecting() {
      doNothing().when(uut).doConnect();

      dfo.onError(new RetryableException(new IOException()));

      verify(subscription).close();
      verify(uut).reConnect();
      verify(store).reset();
      verify(uut).doConnect();
      assertThat(uut.resilience().numberOfAttemptsInWindow()).isOne();
    }

    @Test
    void delegatesFlush() {
      dfo.flush();
      verify(obs).flush();
    }
  }

  @Nested
  class SubscriptionHolderTest {
    private SubscriptionHolder sh;

    @BeforeEach
    void setup() {
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
    void blocksUntilTimeoutReached() {
      assertThatThrownBy(() -> sh.getAndBlock(100)).isInstanceOf(TimeoutException.class);
    }
  }
}
