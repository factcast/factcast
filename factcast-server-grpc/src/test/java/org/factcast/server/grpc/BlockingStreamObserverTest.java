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
package org.factcast.server.grpc;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.ServerCallStreamObserver;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class BlockingStreamObserverTest {

  @Mock private ServerCallStreamObserver<Object> delegate;

  private BlockingStreamObserver<Object> uut;

  @BeforeEach
  void setUp() {
    uut = new BlockingStreamObserver<>("foo", delegate, 1);
  }

  @Test
  void testOnCompleted() {
    verify(delegate, never()).onCompleted();
    uut.onCompleted();
    verify(delegate).onCompleted();
  }

  @Test
  void testOnErrorDelegates() {
    verify(delegate, never()).onError(any());
    uut.onError(new Exception());
    verify(delegate).onError(any());
  }

  @Test
  void testOnErrorDelegatesSreWithoutTransforming() {
    StatusRuntimeException sre = new StatusRuntimeException(Status.NOT_FOUND);
    verify(delegate, never()).onError(any());
    uut.onError(sre);
    verify(delegate).onError(sre);
  }

  @Test
  void testOnErrorTranslatesToSre() {
    Exception ioException = new IOException("i want to be wrapped");
    verify(delegate, never()).onError(any());
    uut.onError(ioException);
    verify(delegate).onError(any(StatusRuntimeException.class));
  }

  @Test
  void testOnNextWhenReady() {
    when(delegate.isReady()).thenReturn(true);
    uut.onNext(new Object());
    verify(delegate).onNext(any());
  }

  @Test
  void testOnNextNotYetReady() throws Exception {
    AtomicBoolean ready = new AtomicBoolean(false);
    when(delegate.isReady()).thenAnswer(i -> ready.get());
    CompletableFuture<Void> onNextCall = CompletableFuture.runAsync(() -> uut.onNext(new Object()));
    Thread.sleep(30);
    assertFalse(onNextCall.isDone());
    ready.set(true);
    uut.wakeup();
    Thread.sleep(30);
    assertTrue(onNextCall.isDone());
    verify(delegate).onNext(any());
  }

  @Test
  void testOnNextNotReadyThenCancelled() throws Exception {
    CountDownLatch waitForDelegate = new CountDownLatch(1);
    CountDownLatch waitForOnNextToExit = new CountDownLatch(1);
    uut =
        new BlockingStreamObserver<>("foo", delegate, 1) {
          @Override
          void waitForDelegate() {
            waitForDelegate.countDown();
            super.waitForDelegate();
          }
        };

    AtomicBoolean ready = new AtomicBoolean(false);
    AtomicBoolean cancelled = new AtomicBoolean(false);

    when(delegate.isReady()).thenAnswer(i -> ready.get());
    when(delegate.isCancelled()).thenAnswer(i -> cancelled.get());
    CompletableFuture<Void> onNextCall =
        CompletableFuture.runAsync(
            () -> {
              // this will block becaue of not being ready
              uut.onNext(new Object());
              // we get here, once woken up after set to cancel
              waitForOnNextToExit.countDown();
            });
    // waiting for onNext to be in blocking state
    waitForDelegate.await();
    cancelled.set(true);
    uut.wakeup();
    waitForOnNextToExit.await();
    // onNext must never have been called
    verify(delegate, never()).onNext(any());
  }

  public static void expectNPE(Runnable r) {
    expect(r, NullPointerException.class, IllegalArgumentException.class);
  }

  @SafeVarargs
  public static void expect(Runnable r, Class<? extends Throwable>... ex) {
    try {
      r.run();
      fail("expected " + Arrays.toString(ex));
    } catch (Throwable actual) {

      var matches = Arrays.stream(ex).anyMatch(e -> e.isInstance(actual));
      if (!matches) {
        fail("Wrong exception, expected " + Arrays.toString(ex) + " but got " + actual);
      }
    }
  }
}
