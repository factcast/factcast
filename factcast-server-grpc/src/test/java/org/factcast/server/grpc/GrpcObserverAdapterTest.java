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
import static org.assertj.core.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.grpc.stub.StreamObserver;
import java.util.Arrays;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.Function;
import lombok.NonNull;
import org.factcast.core.Fact;
import org.factcast.core.subscription.FactStreamInfo;
import org.factcast.core.subscription.observer.FastForwardTarget;
import org.factcast.grpc.api.conv.ProtoConverter;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_Notification;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_Notification.Type;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@SuppressWarnings({"rawtypes", "unchecked", "deprecation"})
@ExtendWith(MockitoExtension.class)
public class GrpcObserverAdapterTest {

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
    when(mockGrpcRequestMetaData.catchupBatch()).thenReturn(OptionalInt.of(1));

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
    when(mockGrpcRequestMetaData.catchupBatch()).thenReturn(OptionalInt.of(1));

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
    when(mockGrpcRequestMetaData.catchupBatch()).thenReturn(OptionalInt.of(1));

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
    verify(observer).onNext(any());
    assertEquals(MSG_Notification.Type.Fact, msg.getValue().getType());
    assertEquals(f.id(), conv.fromProto(msg.getValue().getFact()).id());
  }

  @Test
  void testOnFastForwardIfSupported() {
    ProtoConverter conv = new ProtoConverter();
    GrpcObserverAdapter uut = new GrpcObserverAdapter("foo", observer);
    UUID id = UUID.randomUUID();
    uut.onFastForward(id);
    verify(observer).onNext(eq(conv.createNotificationForFastForward(id)));
  }

  @Test
  void skipsOnFastForwardIfUnsupported() {
    ProtoConverter conv = new ProtoConverter();
    @NonNull GrpcRequestMetadata meta = mock(GrpcRequestMetadata.class);
    when(meta.supportsFastForward()).thenReturn(false);
    GrpcObserverAdapter uut = new GrpcObserverAdapter("foo", observer, meta);
    UUID id = UUID.randomUUID();
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

  public static void expectNPE(Runnable r) {
    expect(r, NullPointerException.class, IllegalArgumentException.class);
  }

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
