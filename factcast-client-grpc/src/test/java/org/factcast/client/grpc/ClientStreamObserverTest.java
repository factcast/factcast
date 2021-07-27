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

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import lombok.val;
import org.assertj.core.util.Lists;
import org.factcast.core.Fact;
import org.factcast.core.FactValidationException;
import org.factcast.core.subscription.FactTransformers;
import org.factcast.core.subscription.StaleSubscriptionDetected;
import org.factcast.core.subscription.SubscriptionImpl;
import org.factcast.core.subscription.observer.FactObserver;
import org.factcast.grpc.api.conv.ProtoConverter;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_Notification;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_Notification.Type;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClientStreamObserverTest {

  @Mock FactObserver factObserver;

  ClientStreamObserver uut;

  final ProtoConverter converter = new ProtoConverter();

  private SubscriptionImpl subscription;

  @BeforeEach
  void setUp() {
    FactTransformers trans = new NullFactTransformer();
    SubscriptionImpl subscriptionImpl = new SubscriptionImpl(factObserver, trans);
    subscription = spy(subscriptionImpl);
    uut = new ClientStreamObserver(subscription, 0L);
  }

  @Test
  void testConstructorNull() {
    Assertions.assertThrows(NullPointerException.class, () -> new ClientStreamObserver(null, 0L));
  }

  @Test
  void testOnNext() {
    Fact f = Fact.of("{\"ns\":\"ns\",\"id\":\"" + UUID.randomUUID() + "\"}", "{}");
    MSG_Notification n = converter.createNotificationFor(f);
    uut.onNext(n);
    verify(factObserver).onNext(eq(f));
  }

  @Test
  void testFastForward() {
    UUID id = UUID.randomUUID();
    MSG_Notification n = converter.createNotificationForFastForward(id);
    uut.onNext(n);
    verify(factObserver).onFastForward(eq(id));
  }

  @Test
  void testOnNextList() {
    Fact f1 = Fact.of("{\"ns\":\"ns\",\"id\":\"" + UUID.randomUUID() + "\"}", "{}");
    Fact f2 = Fact.of("{\"ns\":\"ns\",\"id\":\"" + UUID.randomUUID() + "\"}", "{}");
    ArrayList<Fact> stagedFacts = Lists.newArrayList(f1, f2);
    MSG_Notification n = converter.createNotificationFor(stagedFacts);
    uut.onNext(n);
    verify(factObserver, times(2)).onNext(any(Fact.class));
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

    val e = new FactValidationException("disappointed");
    val metadata = new Metadata();
    metadata.put(
        Metadata.Key.of("msg-bin", Metadata.BINARY_BYTE_MARSHALLER), e.getMessage().getBytes());
    metadata.put(
        Metadata.Key.of("exc-bin", Metadata.BINARY_BYTE_MARSHALLER),
        e.getClass().getName().getBytes());

    val ex = new StatusRuntimeException(Status.UNKNOWN, metadata);

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

    verify(subscription).notifyError(any(StaleSubscriptionDetected.class));
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
      Thread.sleep(10);
    }

    boolean await = latch.await(300L, TimeUnit.MILLISECONDS);
    assertThat(await).isTrue();

    verify(subscription).notifyError(any(StaleSubscriptionDetected.class));
  }

  @Test
  void disablesKeepaliveCheckingOnError() throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(1);
    uut = spy(new ClientStreamObserver(subscription, 10L));
    uut.onError(new RuntimeException());
    verify(uut).disableKeepalive();
  }

  @Test
  void disablesKeepaliveCheckingOnComplete() throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(1);
    uut = spy(new ClientStreamObserver(subscription, 10L));
    uut.onCompleted();
    verify(uut).disableKeepalive();
  }
}
