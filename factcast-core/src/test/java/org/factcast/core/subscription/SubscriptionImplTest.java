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
package org.factcast.core.subscription;

import static org.assertj.core.api.Assertions.*;
import static org.factcast.core.TestHelper.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeoutException;
import lombok.NonNull;
import org.factcast.core.Fact;
import org.factcast.core.TestFact;
import org.factcast.core.subscription.observer.FactObserver;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class SubscriptionImplTest {

  @Mock private FactObserver observer;

  @Mock private FactTransformers factTransformers;

  @InjectMocks private SubscriptionImpl uut;

  @BeforeEach
  void setUp() {
    obs = mock(FactObserver.class);
  }

  @Test
  void testClose() {
    expect(TimeoutException.class, () -> uut.awaitCatchup(10));
    expect(TimeoutException.class, () -> uut.awaitComplete(10));
    uut.close();
    expect(SubscriptionClosedException.class, () -> uut.awaitCatchup(10));
    expect(SubscriptionClosedException.class, () -> uut.awaitComplete(10));
  }

  @Test
  void onCloseStacksUpAndIgnoresException() {

    class DoNothing implements Runnable {
      @Override
      public void run() {}
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
  void testAwaitCatchup() {
    expect(TimeoutException.class, () -> uut.awaitCatchup(10));
    expect(TimeoutException.class, () -> uut.awaitComplete(10));
    uut.notifyCatchup();
    uut.awaitCatchup();
    expect(TimeoutException.class, () -> uut.awaitComplete(10));
  }

  @Test
  void testAwaitComplete() {
    expect(TimeoutException.class, () -> uut.awaitCatchup(10));
    expect(TimeoutException.class, () -> uut.awaitComplete(10));
    uut.notifyComplete();
    uut.awaitCatchup();
    uut.awaitComplete();
  }

  @Test
  void testNullConst() {
    Assertions.assertThrows(NullPointerException.class, () -> new SubscriptionImpl(null, null));
  }

  @Test
  void testNotifyElementNull() {
    Assertions.assertThrows(NullPointerException.class, () -> uut.notifyElement(null));
  }

  @Test
  void testNotifyErrorNull() {
    Assertions.assertThrows(NullPointerException.class, () -> uut.notifyError(null));
  }

  @Test
  void testOnClose() throws Exception {
    CountDownLatch l = new CountDownLatch(1);
    uut.onClose(l::countDown);
    uut.close();
    l.await();
  }

  private FactObserver obs;

  private final FactTransformers ft = e -> e;

  @Test
  void testOnNull() {
    Assertions.assertThrows(NullPointerException.class, () -> SubscriptionImpl.on(null, null));
  }

  final Fact testFact = new TestFact();
  final Fact transformedTestFact =
      new TestFact().id(testFact.id()); // just a different instance than testfact

  @Test
  void testOn() throws TransformationException {
    SubscriptionImpl on = SubscriptionImpl.on(obs, ft);
    verify(obs, never()).onNext(any());
    on.notifyElement(testFact);
    verify(obs).onNext(testFact);
    verify(obs, never()).onCatchup();
    on.notifyCatchup();
    verify(obs).onCatchup();
    verify(obs, never()).onComplete();
    on.notifyComplete();
    verify(obs).onComplete();
    // subsequent calls will be ignored, as this subscription was completed.
    // creating a new one...
  }

  @Test
  void testOnElementIncrementsCounters() throws TransformationException {
    SubscriptionImpl on = SubscriptionImpl.on(obs, factTransformers);
    verify(obs, never()).onNext(any());
    when(factTransformers.transformIfNecessary(testFact))
        .thenReturn(testFact, testFact, transformedTestFact);

    on.notifyElement(testFact);
    verify(obs).onNext(testFact);
    assertThat(on.factsTransformed().get()).isEqualTo(0);
    assertThat(on.factsNotTransformed().get()).isEqualTo(1);

    on.notifyElement(testFact);
    verify(obs, times(2)).onNext(testFact);
    assertThat(on.factsTransformed().get()).isEqualTo(0);
    assertThat(on.factsNotTransformed().get()).isEqualTo(2);

    on.notifyElement(testFact);
    verify(obs, times(3)).onNext(testFact);
    assertThat(on.factsTransformed().get()).isEqualTo(1);
    assertThat(on.factsNotTransformed().get()).isEqualTo(2);
  }

  @Test
  void testOnError() {
    SubscriptionImpl on = SubscriptionImpl.on(obs, ft);
    verify(obs, never()).onError(any());
    on.notifyError(new Throwable("ignore me"));
    verify(obs).onError(any());
  }

  @Test
  void testOnErrorCloses() throws TransformationException {
    SubscriptionImpl on = SubscriptionImpl.on(obs, ft);
    on.notifyError(new Throwable("ignore me"));
    on.notifyElement(testFact);
    on.notifyCatchup();
    on.notifyComplete();
    verify(obs, never()).onComplete();
    verify(obs, never()).onCatchup();
    verify(obs, never()).onNext(any());
  }

  @Test
  void testOnCompleteCloses() throws TransformationException {
    SubscriptionImpl on = SubscriptionImpl.on(obs, ft);
    on.notifyComplete();
    on.notifyElement(testFact);
    on.notifyCatchup();
    on.notifyError(new Throwable("ignore me"));
    verify(obs, never()).onError(any());
    verify(obs, never()).onCatchup();
    verify(obs, never()).onNext(any());
  }

  @Test
  void testOnCatchupDoesNotClose() throws TransformationException {
    SubscriptionImpl on = SubscriptionImpl.on(obs, ft);
    on.notifyCatchup();
    on.notifyElement(testFact);
    on.notifyError(new Throwable("ignore me"));
    verify(obs).onError(any());
    verify(obs).onCatchup();
    verify(obs).onNext(testFact);
  }

  @Test
  void testOnFastForwardDelegatesIfNotClosed() throws TransformationException {
    SubscriptionImpl on = SubscriptionImpl.on(obs, ft);
    @NonNull UUID id = UUID.randomUUID();
    on.notifyFastForward(id);
    verify(obs).onFastForward(id);
  }

  @Test
  void testOnFastForwardSkipsIfClosed() throws TransformationException {
    SubscriptionImpl on = SubscriptionImpl.on(obs, ft);
    @NonNull UUID id = UUID.randomUUID();
    on.close();
    on.notifyFastForward(id);
    verify(obs, never()).onFastForward(id);
  }

  @Test
  void testOnErrorCompletesFutureCatchup() {
    Assertions.assertThrows(
        Throwable.class,
        () -> {
          SubscriptionImpl on = SubscriptionImpl.on(obs, ft);
          verify(obs, never()).onError(any());
          on.notifyError(new Throwable("ignore me"));
          verify(obs).onError(any());
          on.awaitCatchup();
        });
  }

  @Test
  void testOnErrorCompletesFutureComplete() {
    Assertions.assertThrows(
        Throwable.class,
        () -> {
          SubscriptionImpl on = SubscriptionImpl.on(obs, ft);
          verify(obs, never()).onError(any());
          on.notifyError(new Throwable("ignore me"));
          verify(obs).onError(any());
          on.awaitComplete();
        });
  }

  @Test
  void testAwaitCatchupLong() {
    Assertions.assertTimeout(
        Duration.ofMillis(100),
        () -> {
          uut.notifyCatchup();
          uut.awaitCatchup(100000);
        });
  }

  @Test
  void testAwaitCompleteLong() {
    Assertions.assertTimeout(
        Duration.ofMillis(100),
        () -> {
          uut.notifyComplete();
          uut.awaitComplete(100000);
        });
  }

  @Test
  public void testCloseThrowsException() {
    uut = spy(uut);
    doThrow(RuntimeException.class).when(uut).close();

    // this must return without exceptions
    uut.notifyComplete();
  }

  @Test
  void notifyInfoDelegatesIfNotClosed() {

    FactStreamInfo fsi = new FactStreamInfo(1, 10);
    uut.notifyFactStreamInfo(fsi);

    verify(observer).onFactStreamInfo(eq(fsi));
  }

  @Test
  void notifyInfoSkipsIfClosed() throws TransformationException {
    SubscriptionImpl on = SubscriptionImpl.on(obs, ft);
    @NonNull UUID id = UUID.randomUUID();
    FactStreamInfo fsi = new FactStreamInfo(1, 10);
    uut.notifyFactStreamInfo(fsi);
    verify(obs, never()).onFactStreamInfo(any());
  }
}
