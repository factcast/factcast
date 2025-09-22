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

import static org.assertj.core.api.Assertions.*;
import static org.factcast.server.grpc.metrics.ServerMetrics.EVENT.BYTES_SENT;
import static org.factcast.server.grpc.metrics.ServerMetrics.EVENT.FACTS_SENT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import io.grpc.stub.StreamObserver;
import io.micrometer.core.instrument.Tags;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.Function;
import lombok.NonNull;
import org.assertj.core.api.Assertions;
import org.factcast.core.Fact;
import org.factcast.core.FactStreamPosition;
import org.factcast.core.TestFact;
import org.factcast.core.TestFactStreamPosition;
import org.factcast.core.subscription.FactStreamInfo;
import org.factcast.core.subscription.observer.FastForwardTarget;
import org.factcast.grpc.api.conv.ProtoConverter;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_Notification;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_Notification.Type;
import org.factcast.server.grpc.metrics.ServerMetrics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@SuppressWarnings({"rawtypes", "unchecked", "deprecation"})
@ExtendWith(MockitoExtension.class)
class GrpcObserverAdapterTest {

  @Mock private StreamObserver<MSG_Notification> observer;

  @Mock private Function<Fact, MSG_Notification> projection;

  @Mock private ServerExceptionLogger serverExceptionLogger;

  @Captor private ArgumentCaptor<MSG_Notification> msg;

  @Test
  void testOnComplete() {
    GrpcObserverAdapter uut = new GrpcObserverAdapter("foo", observer, serverExceptionLogger);
    uut.onComplete();
    verify(observer).onCompleted();
  }

  @Test
  void testOnCompleteWithException() {
    GrpcObserverAdapter uut = new GrpcObserverAdapter("foo", observer, serverExceptionLogger);
    doThrow(UnsupportedOperationException.class).when(observer).onCompleted();
    uut.onComplete();
    verify(observer).onCompleted();
  }

  @Test
  void testOnCatchup() {
    GrpcObserverAdapter uut = new GrpcObserverAdapter("foo", observer, serverExceptionLogger);
    doNothing().when(observer).onNext(msg.capture());
    verify(observer, never()).onNext(any());
    uut.onCatchup();
    verify(observer).onNext(any());
    assertEquals(MSG_Notification.Type.Catchup, msg.getValue().getType());
  }

  @Test
  void testFactStreamInfo() {
    GrpcObserverAdapter uut = new GrpcObserverAdapter("foo", observer, serverExceptionLogger);
    FactStreamInfo info = new FactStreamInfo(2, 3);
    uut.onFactStreamInfo(info);
    verify(observer).onNext(eq(new ProtoConverter().createInfoNotification(info)));
  }

  @Test
  void testOnCatchupWithFfwd_noTarget() {

    GrpcRequestMetadata mockGrpcRequestMetaData = mock(GrpcRequestMetadata.class);
    when(mockGrpcRequestMetaData.supportsFastForward()).thenReturn(true);
    when(mockGrpcRequestMetaData.clientIdAsString()).thenReturn("testClient");

    FastForwardTarget ffwd = FastForwardTarget.of(null, 112);

    GrpcObserverAdapter uut =
        new GrpcObserverAdapter("foo", observer, mockGrpcRequestMetaData, serverExceptionLogger);

    doNothing().when(observer).onNext(msg.capture());
    verify(observer, never()).onNext(any());
    uut.onCatchup();
    verify(observer, times(1)).onNext(any());
    assertEquals(Type.Catchup, msg.getAllValues().get(0).getType());
  }

  @Test
  void testOnCatchupWithFfwd_noTargetSer() {

    GrpcRequestMetadata mockGrpcRequestMetaData = mock(GrpcRequestMetadata.class);
    when(mockGrpcRequestMetaData.supportsFastForward()).thenReturn(true);
    when(mockGrpcRequestMetaData.clientIdAsString()).thenReturn("testClient");

    FastForwardTarget ffwd = FastForwardTarget.of(new UUID(1, 1), 0);

    GrpcObserverAdapter uut =
        new GrpcObserverAdapter("foo", observer, mockGrpcRequestMetaData, serverExceptionLogger);

    doNothing().when(observer).onNext(msg.capture());
    verify(observer, never()).onNext(any());
    uut.onCatchup();
    verify(observer, times(1)).onNext(any());
    assertEquals(Type.Catchup, msg.getAllValues().get(0).getType());
  }

  @Test
  void testOnCatchupWithoutFfwd_disabled() {

    GrpcRequestMetadata mockGrpcRequestMetaData = mock(GrpcRequestMetadata.class);
    when(mockGrpcRequestMetaData.supportsFastForward()).thenReturn(false);
    when(mockGrpcRequestMetaData.clientIdAsString()).thenReturn("testClient");

    FastForwardTarget ffwd = FastForwardTarget.of(new UUID(10, 10), 112);

    GrpcObserverAdapter uut =
        new GrpcObserverAdapter("foo", observer, mockGrpcRequestMetaData, serverExceptionLogger);

    doNothing().when(observer).onNext(msg.capture());
    verify(observer, never()).onNext(any());
    uut.onCatchup();
    verify(observer, times(1)).onNext(any());
    assertEquals(Type.Catchup, msg.getAllValues().get(0).getType());
  }

  @Test
  void testOnError() {
    GrpcObserverAdapter uut = new GrpcObserverAdapter("foo", observer, serverExceptionLogger);
    var exception = new Exception();
    verify(observer, never()).onNext(any());
    uut.onError(exception);
    verify(observer).onError(any());
    verify(serverExceptionLogger).log(exception, "foo");
  }

  @Test
  void testOnNext() {
    ProtoConverter conv = new ProtoConverter();
    GrpcObserverAdapter uut = new GrpcObserverAdapter("foo", observer);
    doNothing().when(observer).onNext(msg.capture());
    verify(observer, never()).onNext(any());
    Fact f = Fact.builder().ns("test").build("{}");
    uut.onNext(f);
    uut.flush();
    verify(observer).onNext(any());
    MSG_Notification notification = msg.getValue();
    assertEquals(MSG_Notification.Type.Facts, notification.getType());
    assertEquals(f, conv.fromProto(notification.getFacts()).get(0));
  }

  @Test
  void testOnFastForwardIfSupported() {
    ProtoConverter conv = new ProtoConverter();
    GrpcObserverAdapter uut = new GrpcObserverAdapter("foo", observer);
    FactStreamPosition id = TestFactStreamPosition.random();
    uut.onFastForward(id);
    verify(observer).onNext(eq(conv.toProto(id)));
  }

  @Test
  void skipsOnFastForwardIfUnsupported() {
    @NonNull GrpcRequestMetadata meta = mock(GrpcRequestMetadata.class);
    when(meta.supportsFastForward()).thenReturn(false);
    when(meta.clientIdAsString()).thenReturn("testClient");
    GrpcObserverAdapter uut = new GrpcObserverAdapter("foo", observer, meta);
    FactStreamPosition id = TestFactStreamPosition.random();
    uut.onFastForward(id);
    verify(observer, never()).onNext(any());
  }

  @Test
  void createKeepAliveMonitor() {
    GrpcObserverAdapter uut = new GrpcObserverAdapter("foo", observer, 300);
    assertThat(uut.keepalive()).isNotNull();
  }

  @Test
  void doesNotCreateKeepAliveMonitorIfUnnecessary() {
    GrpcObserverAdapter uut = new GrpcObserverAdapter("foo", observer, 0);
    assertThat(uut.keepalive()).isNull();
  }

  @Test
  void shutdownDelegates() {
    GrpcObserverAdapter uut = new GrpcObserverAdapter("foo", observer, 3000);
    uut.shutdown();

    // if keepalive is shutdown, reschedule should throw illegalstateexceptions
    assertThatThrownBy(
            () -> {
              uut.keepalive().reschedule();
            })
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void shutdownIgnoredWhenNoKeepalive() {
    assertThatNoException()
        .isThrownBy(
            () -> {
              GrpcObserverAdapter uut = new GrpcObserverAdapter("foo", observer, 0);
              uut.shutdown();
            });
    // should no
  }

  @Test
  void testFlushOnComplete() {
    GrpcObserverAdapter uut = new GrpcObserverAdapter("foo", observer, serverExceptionLogger);
    Fact f1 = new TestFact();
    Fact f2 = new TestFact();
    Fact f3 = new TestFact();
    uut.onNext(f1);
    uut.onNext(f2);
    uut.onNext(f3);
    uut.onComplete();

    ArgumentCaptor<MSG_Notification> cap = ArgumentCaptor.forClass(MSG_Notification.class);
    verify(observer, times(2)).onNext(cap.capture());

    MSG_Notification msg1 = cap.getAllValues().get(0);
    MSG_Notification msg2 = cap.getAllValues().get(1);
    Assertions.assertThat(msg1.getFacts().getFactCount()).isEqualTo(3);
    Assertions.assertThat(msg2.getType()).isEqualTo(Type.Complete);

    verify(observer).onCompleted();
  }

  @Test
  void testFlushDelegation() {
    GrpcObserverAdapter uut = new GrpcObserverAdapter("foo", observer, serverExceptionLogger);
    Fact f1 = new TestFact();
    Fact f2 = new TestFact();
    Fact f3 = new TestFact();
    uut.onNext(f1);
    uut.onNext(f2);
    uut.flush();
    uut.onNext(f3);
    uut.flush();

    ArgumentCaptor<MSG_Notification> cap = ArgumentCaptor.forClass(MSG_Notification.class);
    verify(observer, times(2)).onNext(cap.capture());

    MSG_Notification msg1 = cap.getAllValues().get(0);
    MSG_Notification msg2 = cap.getAllValues().get(1);
    Assertions.assertThat(msg1.getFacts().getFactCount()).isEqualTo(2);
    Assertions.assertThat(msg2.getFacts().getFactCount()).isOne();

    verifyNoMoreInteractions(observer);
  }

  @Test
  void testMetricsOnFlush() {
    ServerMetrics metrics = mock(ServerMetrics.class);
    GrpcRequestMetadata meta = mock(GrpcRequestMetadata.class);
    when(meta.clientMaxInboundMessageSize()).thenReturn(1024);
    when(meta.clientIdAsString()).thenReturn("testClient");
    GrpcObserverAdapter uut =
        new GrpcObserverAdapter("foo", observer, meta, serverExceptionLogger, metrics, 1L);
    Fact f1 = new TestFact();
    Fact f2 = new TestFact();
    uut.onNext(f1);
    uut.onNext(f2);

    uut.flush();

    var expectedBytes =
        f1.jsonHeader().getBytes(StandardCharsets.UTF_8).length
            + f1.jsonPayload().getBytes(StandardCharsets.UTF_8).length
            + f2.jsonHeader().getBytes(StandardCharsets.UTF_8).length
            + f2.jsonPayload().getBytes(StandardCharsets.UTF_8).length
            + 16; // protobuf overhead
    verify(metrics)
        .count(
            BYTES_SENT,
            Tags.of(ServerMetrics.MetricsTag.CLIENT_ID_KEY, "testClient"),
            expectedBytes);
    verify(metrics)
        .count(FACTS_SENT, Tags.of(ServerMetrics.MetricsTag.CLIENT_ID_KEY, "testClient"), 2);
  }
}
