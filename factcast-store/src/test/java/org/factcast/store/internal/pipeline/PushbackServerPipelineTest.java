/*
 * Copyright © 2017-2026 factcast.org
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
package org.factcast.store.internal.pipeline;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.*;
import org.factcast.core.Fact;
import org.factcast.core.subscription.SubscriptionImpl;
import org.factcast.core.subscription.observer.FactObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PushbackServerPipelineTest {
  @Mock private ServerPipeline delegate;

  private PushbackServerPipeline underTest;

  @BeforeEach
  void setUp() {
    underTest = new PushbackServerPipeline(delegate);
  }

  @Test
  void testProcessDelegates() {
    Signal signal = Signal.catchup();
    underTest.process(signal);
    verify(delegate).process(signal);
  }

  @Test
  void testProcessThrowsWhenClosed() {
    Signal signal = Signal.catchup();
    underTest.close();
    assertThatThrownBy(() -> underTest.process(signal))
        .isInstanceOf(PipelineAlreadyClosedException.class);
    verify(delegate, never()).process(any());
  }

  @Test
  void testCloseDelegates() {
    underTest.close();
    verify(delegate).close();
  }

  @Test
  void testProcessNullSignalThrows() {
    assertThatThrownBy(() -> underTest.process(null)).isInstanceOf(NullPointerException.class);
  }

  @Test
  void completingWhileClientClosesDoesNotDeadlock() throws Exception {
    CountDownLatch observerCompleted = new CountDownLatch(1);
    CountDownLatch clientClosing = new CountDownLatch(1);
    SubscriptionImpl subscription =
        SubscriptionImpl.on(
            new FactObserver() {
              @Override
              public void onNext(Fact fact) {}

              @Override
              public void onComplete() {
                observerCompleted.countDown();
                await(clientClosing);
              }
            });
    PushbackServerPipeline pipeline =
        new PushbackServerPipeline(new ServerPipelineAdapter(subscription));
    subscription.onClose(
        () -> {
          clientClosing.countDown();
          pipeline.close();
        });

    // Daemon threads let a regression fail with a timeout instead of hanging the test JVM.
    ExecutorService executor =
        Executors.newFixedThreadPool(
            2,
            task -> {
              Thread thread = new Thread(task, "subscription-close-regression");
              thread.setDaemon(true);
              return thread;
            });
    try {
      Future<?> completing = executor.submit(() -> pipeline.process(Signal.complete()));
      assertThat(observerCompleted.await(5, TimeUnit.SECONDS)).isTrue();
      Future<?> closing = executor.submit(subscription::close);
      completing.get(5, TimeUnit.SECONDS);
      closing.get(5, TimeUnit.SECONDS);
      assertThat(pipeline.isClosed()).isTrue();
    } finally {
      executor.shutdownNow();
    }
  }

  private static void await(CountDownLatch latch) {
    try {
      assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError(e);
    }
  }
}
