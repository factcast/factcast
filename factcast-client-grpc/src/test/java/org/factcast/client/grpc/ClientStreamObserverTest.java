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
import static org.mockito.Mockito.*;

import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import lombok.SneakyThrows;
import org.assertj.core.util.Lists;
import org.factcast.core.Fact;
import org.factcast.core.FactStreamPosition;
import org.factcast.core.FactValidationException;
import org.factcast.core.TestFactStreamPosition;
import org.factcast.core.subscription.FactStreamInfo;
import org.factcast.core.subscription.StaleSubscriptionDetectedException;
import org.factcast.core.subscription.SubscriptionImpl;
import org.factcast.core.subscription.observer.BatchingFactObserver;
import org.factcast.grpc.api.conv.ProtoConverter;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_Notification;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_Notification.Type;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClientStreamObserverTest {

  @Mock BatchingFactObserver factObserver;

  ClientStreamObserver uut;

  final ProtoConverter converter = new ProtoConverter();

  private SubscriptionImpl subscription;

  @BeforeEach
  void setUp() {
    SubscriptionImpl subscriptionImpl = new SubscriptionImpl(factObserver);
    subscription = spy(subscriptionImpl);
    uut = new ClientStreamObserver(subscription, 0L);
  }

  @Test
  void testConstructorNull() {
    Assertions.assertThrows(NullPointerException.class, () -> new ClientStreamObserver(null, 0L));
  }

  @Test
  void registersForCleanup() {
    verify(subscription, times(2)).onClose(any());
  }

  @Test
  void shutsdownOnSubscriptionClose() {
    subscription.close();
    assertThat(uut.clientBoundExecutor().isShutdown()).isTrue();
  }

  @Test
  void shutsdownOnErrorRecieved() {
    assertThat(uut.clientBoundExecutor().isShutdown()).isFalse();
    uut.onError(new IOException());
    assertThat(uut.clientBoundExecutor().isShutdown()).isTrue();
  }

  @Test
  void shutsdownOnCompleteRecieved() {
    assertThat(uut.clientBoundExecutor().isShutdown()).isFalse();
    uut.onCompleted();
    assertThat(uut.clientBoundExecutor().isShutdown()).isTrue();
  }

  @Test
  void rethrowsProcessingError() {
    doThrow(new UnsupportedOperationException()).when(factObserver).onNext(any());

    Fact f = Fact.of("{\"ns\":\"ns\",\"id\":\"" + UUID.randomUUID() + "\"}", "{}");
    MSG_Notification n = converter.createNotificationFor(f);
    assertThatThrownBy(() -> uut.onNext(n)).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void testOnNext() {
    Fact f = Fact.of("{\"ns\":\"ns\",\"id\":\"" + UUID.randomUUID() + "\"}", "{}");
    MSG_Notification n = converter.createNotificationFor(f);
    uut.onNext(n);
    verify(factObserver).onNext(Collections.singletonList(f));
  }

  @Test
  void testFastForward() {
    FactStreamPosition p = TestFactStreamPosition.random();
    MSG_Notification n = converter.createNotificationForFastForward(p);
    uut.onNext(n);
    verify(factObserver).onFastForward(p);
  }

  @SuppressWarnings("unchecked")
  @Test
  void testOnNextListDelegates() {
    Fact f1 = Fact.of("{\"ns\":\"ns\",\"id\":\"" + UUID.randomUUID() + "\"}", "{}");
    Fact f2 = Fact.of("{\"ns\":\"ns\",\"id\":\"" + UUID.randomUUID() + "\"}", "{}");
    ArrayList<Fact> stagedFacts = Lists.newArrayList(f1, f2);
    MSG_Notification n = converter.createNotificationFor(stagedFacts);
    uut.onNext(n);
    ArgumentCaptor<List<Fact>> cap = ArgumentCaptor.forClass(List.class);
    verify(factObserver).onNext(cap.capture());
    org.assertj.core.api.Assertions.assertThat(cap.getValue()).hasSize(2).containsExactly(f1, f2);
  }

  @Test
  void testOnNextList() {
    Fact f1 = Fact.of("{\"ns\":\"ns\",\"id\":\"" + UUID.randomUUID() + "\"}", "{}");
    Fact f2 = Fact.of("{\"ns\":\"ns\",\"id\":\"" + UUID.randomUUID() + "\"}", "{}");
    ArrayList<Fact> stagedFacts = Lists.newArrayList(f1, f2);
    MSG_Notification n = converter.createNotificationFor(stagedFacts);
    uut.onNext(n);
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Fact>> l = ArgumentCaptor.forClass(List.class);
    verify(factObserver, times(1)).onNext(l.capture());

    org.assertj.core.api.Assertions.assertThat(l.getValue())
        .isNotNull()
        .hasSize(2)
        .containsExactly(f1, f2);
  }

  @Test
  void testOnNextFailsOnUnknownMessage() {
    assertThrows(
        IllegalArgumentException.class,
        () -> {
          MSG_Notification n = MSG_Notification.newBuilder().setType(Type.UNRECOGNIZED).build();
          uut.onNext(n);
        });
  }

  @Test
  void testOnCatchup() {
    uut.onNext(converter.createCatchupNotification());
    verify(factObserver).onCatchup();
  }

  @Test
  void testFailOnUnknownType() {
    uut.onNext(MSG_Notification.newBuilder().setTypeValue(999).build());
    verify(subscription).notifyError(any(RuntimeException.class));
  }

  @Test
  void testOnComplete() {
    uut.onNext(converter.createCompleteNotification());
    verify(factObserver).onComplete();
  }

  @Test
  void testOnTransportComplete() {
    uut.onCompleted();
    verify(factObserver).onComplete();
  }

  @Test
  void testOnError() {
    uut.onError(new IOException());
    verify(factObserver).onError(any());
  }

  @Test
  void translatesException() {

    FactValidationException e = new FactValidationException("disappointed");
    Metadata metadata = new Metadata();
    metadata.put(
        Metadata.Key.of("msg-bin", Metadata.BINARY_BYTE_MARSHALLER), e.getMessage().getBytes());
    metadata.put(
        Metadata.Key.of("exc-bin", Metadata.BINARY_BYTE_MARSHALLER),
        e.getClass().getName().getBytes());

    StatusRuntimeException ex = new StatusRuntimeException(Status.UNKNOWN, metadata);

    uut.onError(ex);

    ArgumentCaptor<Throwable> ecap = ArgumentCaptor.forClass(Throwable.class);
    verify(factObserver).onError(ecap.capture());
    assertThat(ecap.getValue()).isInstanceOf(FactValidationException.class);
  }

  @Test
  void detectsStillbirth() throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(1);
    uut =
        new ClientStreamObserver(subscription, 10L) {
          @Override
          public void onError(Throwable t) {
            super.onError(t);
            latch.countDown();
          }
        };

    boolean await = latch.await(300L, TimeUnit.MILLISECONDS);
    assertThat(await).isTrue();

    verify(subscription).notifyError(any(StaleSubscriptionDetectedException.class));
  }

  @Test
  void detectsStaleness() throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(1);
    uut =
        spy(
            new ClientStreamObserver(subscription, 10L) {
              @Override
              public void onError(Throwable t) {
                super.onError(t);
                latch.countDown();
              }
            });

    uut.onNext(MSG_Notification.newBuilder().setType(Type.KeepAlive).build());

    for (int i = 0; i < 10; i++) {
      uut.onNext(MSG_Notification.newBuilder().setType(Type.KeepAlive).build());
      sleep(10);
    }

    boolean await = latch.await(300L, TimeUnit.MILLISECONDS);
    assertThat(await).isTrue();

    verify(subscription).notifyError(any(StaleSubscriptionDetectedException.class));
  }

  @Test
  void disablesKeepaliveCheckingOnError() {
    uut = spy(new ClientStreamObserver(subscription, 10L));
    uut.onError(new RuntimeException());
    verify(uut).disableKeepalive();
  }

  @Test
  void disablesKeepaliveCheckingOnComplete() {
    uut = spy(new ClientStreamObserver(subscription, 10L));
    uut.onCompleted();
    verify(uut).disableKeepalive();
  }

  @Test
  void detectsMissingKeepalives() {
    uut = spy(new ClientStreamObserver(subscription, 10L));
    int interval = 50;
    uut.new ClientKeepalive(interval);

    // wait for it
    // after awaits full duration
    verify(uut, after(150).never()).onError(any());
    // fails due to NO notification received during interval (50*2+200)
    // timeout exits immediately, so passed value in millis could be even w/o unnecessarily
    // prolonging test
    verify(uut, timeout(250).times(1)).onError(any(StaleSubscriptionDetectedException.class));
  }

  @Test
  void detectSubscriptionStarvation() {

    long interval = 100L;
    long grace = 2 * interval + 200;

    uut = new ClientStreamObserver(subscription, interval);

    sleep(interval / 2);
    // not yet
    verify(subscription, never()).notifyError(any());

    // prolong interval
    for (int i = 0; i < 5; i++) {
      sleep(interval);
      // any notification will reset the time
      uut.onNext(converter.createKeepaliveNotification());
    }

    // still fine because it is < grace
    // after awaits full duration
    verify(subscription, after(grace - 50).never()).notifyError(any());
    // now it should have triggered an error
    // timeout exits immediately, so passed value in millis could be even larger w/o unnecessarily
    // prolonging test
    verify(subscription, timeout(grace).times(1))
        .notifyError(any(StaleSubscriptionDetectedException.class));
  }

  @Test
  void handlesFactStreamInfo() {
    FactStreamInfo fsi = new FactStreamInfo(1, 10);
    uut.onNext(converter.createInfoNotification(fsi));

    verify(subscription).notifyFactStreamInfo(fsi);
  }

  @SneakyThrows
  private void sleep(long i) {
    Thread.sleep(i);
  }
}
