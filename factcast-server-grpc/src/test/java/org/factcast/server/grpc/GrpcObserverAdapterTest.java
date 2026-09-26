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

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import io.micrometer.core.instrument.Tags;
import java.sql.SQLFeatureNotSupportedException;
import java.util.List;
import lombok.NonNull;
import org.assertj.core.api.Assertions;
import org.factcast.core.Fact;
import org.factcast.core.FactStreamPosition;
import org.factcast.core.TestFact;
import org.factcast.core.TestFactStreamPosition;
import org.factcast.core.subscription.FactStreamInfo;
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

@SuppressWarnings({"deprecation"})
@ExtendWith(MockitoExtension.class)
class GrpcObserverAdapterTest {

  @Mock private StreamObserver<MSG_Notification> observer;

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

    GrpcObserverAdapter uut =
        new GrpcObserverAdapter("foo", observer, mockGrpcRequestMetaData, serverExceptionLogger);

    doNothing().when(observer).onNext(msg.capture());
    verify(observer, never()).onNext(any());
    uut.onCatchup();
    verify(observer, times(1)).onNext(any());
    assertEquals(Type.Catchup, msg.getAllValues().getFirst().getType());
  }

  @Test
  void testOnCatchupWithFfwd_noTargetSer() {

    GrpcRequestMetadata mockGrpcRequestMetaData = mock(GrpcRequestMetadata.class);
    when(mockGrpcRequestMetaData.supportsFastForward()).thenReturn(true);
    when(mockGrpcRequestMetaData.clientIdAsString()).thenReturn("testClient");

    GrpcObserverAdapter uut =
        new GrpcObserverAdapter("foo", observer, mockGrpcRequestMetaData, serverExceptionLogger);

    doNothing().when(observer).onNext(msg.capture());
    verify(observer, never()).onNext(any());
    uut.onCatchup();
    verify(observer, times(1)).onNext(any());
    assertEquals(Type.Catchup, msg.getAllValues().getFirst().getType());
  }

  @Test
  void testOnCatchupWithoutFfwd_disabled() {

    GrpcRequestMetadata mockGrpcRequestMetaData = mock(GrpcRequestMetadata.class);
    when(mockGrpcRequestMetaData.supportsFastForward()).thenReturn(false);
    when(mockGrpcRequestMetaData.clientIdAsString()).thenReturn("testClient");

    GrpcObserverAdapter uut =
        new GrpcObserverAdapter("foo", observer, mockGrpcRequestMetaData, serverExceptionLogger);

    doNothing().when(observer).onNext(msg.capture());
    verify(observer, never()).onNext(any());
    uut.onCatchup();
    verify(observer, times(1)).onNext(any());
    assertEquals(Type.Catchup, msg.getAllValues().getFirst().getType());
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
  void sendsUnsupportedJdbcFeatureAsUnimplemented() {
    GrpcObserverAdapter uut = new GrpcObserverAdapter("foo", observer, serverExceptionLogger);
    var exception = new SQLFeatureNotSupportedException("Operation not yet supported");
    ArgumentCaptor<Throwable> error = ArgumentCaptor.forClass(Throwable.class);

    uut.onError(exception);

    verify(observer).onError(error.capture());
    assertThat(error.getValue())
        .isInstanceOf(StatusRuntimeException.class)
        .extracting(e -> ((StatusRuntimeException) e).getStatus().getCode())
        .isEqualTo(Status.Code.UNIMPLEMENTED);
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
    assertEquals(f, conv.fromProto(notification.getFacts()).getFirst());
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
    assertThatThrownBy(() -> uut.keepalive().reschedule())
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
        new GrpcObserverAdapter("foo", observer, meta, serverExceptionLogger, metrics, 0L);
    Fact f1 = new TestFact();
    Fact f2 = new TestFact();
    uut.onNext(f1);
    uut.onNext(f2);

    uut.flush();

    ArgumentCaptor<MSG_Notification> notification = ArgumentCaptor.forClass(MSG_Notification.class);
    verify(observer).onNext(notification.capture());
    verify(metrics)
        .count(
            BYTES_SENT,
            Tags.of(ServerMetrics.MetricsTag.CLIENT_ID_KEY, "testClient"),
            notification.getValue().getSerializedSize());
    verify(metrics)
        .count(FACTS_SENT, Tags.of(ServerMetrics.MetricsTag.CLIENT_ID_KEY, "testClient"), 2);
  }

  @Test
  void splitsAtTheBatchTargetWithoutLosingOrDuplicatingFacts() {
    ProtoConverter converter = new ProtoConverter();
    Fact first = Fact.builder().ns("test").build("{\"value\":\"ä\"}");
    Fact second = Fact.builder().ns("test").build("{\"value\":\"ö\"}");
    Fact third = Fact.builder().ns("test").build("{\"value\":\"ü\"}");
    int twoFacts = converter.createNotificationFor(List.of(first, second)).getSerializedSize();
    int inbound = twoFacts;
    while (inbound - inbound / 10 < twoFacts) {
      inbound++;
    }
    int limit = inbound;
    GrpcRequestMetadata meta = mock(GrpcRequestMetadata.class);
    when(meta.clientMaxInboundMessageSize()).thenReturn(limit);
    when(meta.clientIdAsString()).thenReturn("testClient");
    GrpcObserverAdapter uut = new GrpcObserverAdapter("foo", observer, meta);

    uut.onNext(first);
    uut.onNext(second);
    uut.onNext(third);
    uut.flush();

    ArgumentCaptor<MSG_Notification> notifications =
        ArgumentCaptor.forClass(MSG_Notification.class);
    verify(observer, times(2)).onNext(notifications.capture());
    assertThat(notifications.getAllValues())
        .extracting(notification -> notification.getFacts().getFactCount())
        .containsExactly(2, 1);
    assertThat(
            notifications.getAllValues().stream()
                .flatMap(notification -> converter.fromProto(notification.getFacts()).stream())
                .map(Fact::id)
                .toList())
        .containsExactly(first.id(), second.id(), third.id());
    assertThat(notifications.getAllValues())
        .allSatisfy(
            notification ->
                assertThat(notification.getSerializedSize()).isLessThanOrEqualTo(limit));
  }

  @Test
  void sendsAnOversizedBatchFactAloneWithinTheInboundLimit() {
    Fact small = Fact.builder().ns("test").build("{}");
    Fact large = Fact.builder().ns("test").build("{\"value\":\"" + "x".repeat(500) + "\"}");
    ProtoConverter converter = new ProtoConverter();
    int inbound = converter.createNotificationFor(List.of(large)).getSerializedSize();
    GrpcRequestMetadata meta = mock(GrpcRequestMetadata.class);
    when(meta.clientMaxInboundMessageSize()).thenReturn(inbound);
    when(meta.clientIdAsString()).thenReturn("testClient");
    GrpcObserverAdapter uut = new GrpcObserverAdapter("foo", observer, meta);

    uut.onNext(small);
    uut.onNext(large);
    uut.flush();

    ArgumentCaptor<MSG_Notification> notifications =
        ArgumentCaptor.forClass(MSG_Notification.class);
    verify(observer, times(2)).onNext(notifications.capture());
    assertThat(notifications.getAllValues())
        .extracting(notification -> notification.getFacts().getFactCount())
        .containsExactly(1, 1);
    assertThat(notifications.getAllValues().get(1).getSerializedSize()).isEqualTo(inbound);
  }

  @Test
  void rejectsAFactThatCannotFitTheClientInboundLimit() {
    Fact large = Fact.builder().ns("test").build("{\"value\":\"" + "x".repeat(500) + "\"}");
    int inbound =
        new ProtoConverter().createNotificationFor(List.of(large)).getSerializedSize() - 1;
    GrpcRequestMetadata meta = mock(GrpcRequestMetadata.class);
    when(meta.clientMaxInboundMessageSize()).thenReturn(inbound);
    when(meta.clientIdAsString()).thenReturn("testClient");
    GrpcObserverAdapter uut = new GrpcObserverAdapter("foo", observer, meta);

    assertThatThrownBy(() -> uut.onNext(large))
        .isInstanceOf(StatusRuntimeException.class)
        .satisfies(
            error ->
                assertThat(((StatusRuntimeException) error).getStatus().getCode())
                    .isEqualTo(Status.Code.RESOURCE_EXHAUSTED));
    verifyNoInteractions(observer);
  }
}
