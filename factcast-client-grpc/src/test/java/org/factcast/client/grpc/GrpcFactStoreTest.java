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
import static org.factcast.core.TestHelper.*;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.google.common.collect.Sets;
import io.grpc.*;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.NonNull;
import lombok.val;
import org.assertj.core.util.Lists;
import org.factcast.core.Fact;
import org.factcast.core.FactValidationException;
import org.factcast.core.snap.Snapshot;
import org.factcast.core.snap.SnapshotId;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.store.RetryableException;
import org.factcast.core.store.StateToken;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.core.subscription.observer.FactObserver;
import org.factcast.grpc.api.CompressionCodecs;
import org.factcast.grpc.api.ConditionalPublishRequest;
import org.factcast.grpc.api.Headers;
import org.factcast.grpc.api.StateForRequest;
import org.factcast.grpc.api.conv.ProtoConverter;
import org.factcast.grpc.api.conv.ProtocolVersion;
import org.factcast.grpc.api.conv.ServerConfig;
import org.factcast.grpc.api.gen.FactStoreProto.*;
import org.factcast.grpc.api.gen.RemoteFactStoreGrpc.RemoteFactStoreBlockingStub;
import org.factcast.grpc.api.gen.RemoteFactStoreGrpc.RemoteFactStoreStub;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GrpcFactStoreTest {

  @InjectMocks GrpcFactStore uut;

  @Mock FactCastGrpcClientProperties properties;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  RemoteFactStoreBlockingStub blockingStub;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  RemoteFactStoreStub stub;

  @Mock FactCastGrpcChannelFactory factory;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  SubscriptionRequestTO req;

  final ProtoConverter conv = new ProtoConverter();

  @Captor ArgumentCaptor<MSG_Facts> factsCap;

  @Mock public Optional<String> credentials;
  @Mock private CompressionCodecs codecs;
  @Mock private ProtoConverter converter;
  @Mock private AtomicBoolean initialized;
  @InjectMocks private GrpcFactStore underTest;

  @Test
  void testPublish() {
    when(blockingStub.publish(factsCap.capture())).thenReturn(MSG_Empty.newBuilder().build());
    TestFact fact = new TestFact();
    uut.publish(Collections.singletonList(fact));
    verify(blockingStub).publish(any());
    MSG_Facts pfacts = factsCap.getValue();
    Fact published = conv.fromProto(pfacts.getFact(0));
    assertEquals(fact.id(), published.id());
  }

  @Test
  void configureCompressionChooseGzipIfAvail() {
    uut.configureCompressionAndMetaData(" gzip,lz3,lz4, lz99");
    verify(stub).withCompression("gzip");
  }

  @Test
  void configureCompressionSkipCompression() {
    uut.configureCompressionAndMetaData("zip,lz3,lz4, lz99");
    verifyNoMoreInteractions(stub);
  }

  @Test
  void configureWithFastForwardEnabled() {
    when(properties.isEnableFastForward()).thenReturn(true);
    val meta = uut.prepareMetaData("lz4");
    assertThat(meta.containsKey(Headers.FAST_FORWARD)).isTrue();
  }

  @Test
  void configureWithFastForwardDisabled() {
    when(properties.isEnableFastForward()).thenReturn(false);
    val meta = uut.prepareMetaData("lz4");
    assertThat(meta.containsKey(Headers.FAST_FORWARD)).isFalse();
  }

  @Test
  void configureWithBatchSize1() {
    when(properties.getCatchupBatchsize()).thenReturn(1);
    val meta = uut.prepareMetaData("lz4");
    assertThat(meta.containsKey(Headers.CATCHUP_BATCHSIZE)).isFalse();
  }

  @Test
  void configureWithBatchSize10() {
    when(properties.getCatchupBatchsize()).thenReturn(10);
    val meta = uut.prepareMetaData("lz4");
    assertThat(meta.get(Headers.CATCHUP_BATCHSIZE)).isEqualTo(String.valueOf(10));
  }

  @Test
  void fetchById() {
    TestFact fact = new TestFact();
    val uuid = fact.id();
    val conv = new ProtoConverter();
    val id = conv.toProto(uuid);
    when(blockingStub.fetchById(eq(id)))
        .thenReturn(
            MSG_OptionalFact.newBuilder().setFact(conv.toProto(fact)).setPresent(true).build());

    val result = uut.fetchById(fact.id());
    assertThat(result).isPresent();
    assertThat(result.get().id()).isEqualTo(uuid);
  }

  @Test
  void fetchByIdAndVersion() {
    TestFact fact = new TestFact();
    val uuid = fact.id();
    val conv = new ProtoConverter();
    val id = conv.toProto(uuid, 100);
    when(blockingStub.fetchByIdAndVersion(eq(id)))
        .thenReturn(
            MSG_OptionalFact.newBuilder().setFact(conv.toProto(fact)).setPresent(true).build());

    val result = uut.fetchByIdAndVersion(fact.id(), 100);
    assertThat(result).isPresent();
    assertThat(result.get().id()).isEqualTo(uuid);
  }

  @Test
  void fetchByIdThrowsRetryable() {
    TestFact fact = new TestFact();
    val uuid = fact.id();
    val id = conv.toProto(uuid);
    when(blockingStub.fetchById(eq(id))).thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));

    assertThatThrownBy(() -> uut.fetchById(fact.id())).isInstanceOf(RetryableException.class);
  }

  @Test
  void fetchByIdAndVersionThrowsRetryable() {
    TestFact fact = new TestFact();
    val uuid = fact.id();
    val id = conv.toProto(uuid, 100);
    when(blockingStub.fetchByIdAndVersion(eq(id)))
        .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));

    assertThatThrownBy(() -> uut.fetchByIdAndVersion(fact.id(), 100))
        .isInstanceOf(RetryableException.class);
  }

  static class SomeException extends RuntimeException {

    static final long serialVersionUID = 1L;
  }

  @Test
  void testPublishPropagatesException() {
    when(blockingStub.publish(any())).thenThrow(new SomeException());
    assertThrows(
        SomeException.class,
        () -> uut.publish(Collections.singletonList(Fact.builder().build("{}"))));
  }

  @Test
  void testPublishPropagatesRetryableExceptionOnUnavailableStatus() {
    when(blockingStub.publish(any())).thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));
    assertThrows(
        RetryableException.class,
        () -> uut.publish(Collections.singletonList(Fact.builder().build("{}"))));
  }

  @Test
  void testCancelNotRetryableExceptionOnUnavailableStatus() {
    ClientCall<MSG_SubscriptionRequest, MSG_Notification> call = mock(ClientCall.class);
    doThrow(new StatusRuntimeException(Status.UNAVAILABLE)).when(call).cancel(any(), any());
    assertThrows(StatusRuntimeException.class, () -> uut.cancel(call));
  }

  @Test
  void testSerialOfPropagatesRetryableExceptionOnUnavailableStatus() {
    when(blockingStub.serialOf(any())).thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));
    assertThrows(RetryableException.class, () -> uut.serialOf(mock(UUID.class)));
  }

  @Test
  void testSerialOf() {
    OptionalLong seven = OptionalLong.of(7);
    when(blockingStub.serialOf(any())).thenReturn(conv.toProto(seven));

    OptionalLong response = uut.serialOf(mock(UUID.class));

    assertEquals(seven, response);
    assertNotSame(seven, response);
  }

  @Test
  void testInitializePropagatesIncompatibleProtocolVersionsOnUnavailableStatus() {
    when(blockingStub.handshake(any())).thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));
    assertThrows(IncompatibleProtocolVersions.class, () -> uut.initialize());
  }

  @Test
  void testEnumerateNamespacesPropagatesRetryableExceptionOnUnavailableStatus() {
    when(blockingStub.enumerateNamespaces(any()))
        .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));
    assertThrows(RetryableException.class, () -> uut.enumerateNamespaces());
  }

  @Test
  void testEnumerateNamespaces() {
    HashSet<String> ns = Sets.newHashSet("foo", "bar");
    when(blockingStub.enumerateNamespaces(conv.empty())).thenReturn(conv.toProto(ns));
    Set<String> enumerateNamespaces = uut.enumerateNamespaces();
    assertEquals(ns, enumerateNamespaces);
    assertNotSame(ns, enumerateNamespaces);
  }

  @Test
  void testEnumerateTypesPropagatesRetryableExceptionOnUnavailableStatus() {
    when(blockingStub.enumerateTypes(any()))
        .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));
    assertThrows(RetryableException.class, () -> uut.enumerateTypes("ns"));
  }

  @Test
  void testEnumerateTypes() {
    HashSet<String> types = Sets.newHashSet("foo", "bar");
    when(blockingStub.enumerateTypes(any())).thenReturn(conv.toProto(types));
    Set<String> enumerateTypes = uut.enumerateTypes("ns");
    assertEquals(types, enumerateTypes);
    assertNotSame(types, enumerateTypes);
  }

  @Test
  void testSubscribeNull() {
    expectNPE(() -> uut.subscribe(null, mock(FactObserver.class)));
    expectNPE(() -> uut.subscribe(null, null));
    expectNPE(() -> uut.subscribe(mock(SubscriptionRequestTO.class), null));
  }

  @Test
  void testCompatibleProtocolVersion() {
    when(blockingStub.withInterceptors(any())).thenReturn(blockingStub);
    when(blockingStub.handshake(any()))
        .thenReturn(conv.toProto(ServerConfig.of(ProtocolVersion.of(1, 1, 0), new HashMap<>())));
    uut.initialize();
  }

  @Test
  void testIncompatibleProtocolVersion() {
    when(blockingStub.withInterceptors(any())).thenReturn(blockingStub);
    when(blockingStub.handshake(any()))
        .thenReturn(conv.toProto(ServerConfig.of(ProtocolVersion.of(99, 0, 0), new HashMap<>())));
    Assertions.assertThrows(IncompatibleProtocolVersions.class, () -> uut.initialize());
  }

  @Test
  void testInitializationExecutesHandshakeOnlyOnce() {
    when(blockingStub.withInterceptors(any())).thenReturn(blockingStub);
    when(blockingStub.handshake(any()))
        .thenReturn(conv.toProto(ServerConfig.of(ProtocolVersion.of(1, 1, 0), new HashMap<>())));
    uut.initialize();
    uut.initialize();
    verify(blockingStub, times(1)).handshake(any());
  }

  @Test
  void testWrapRetryable_nonRetryable() {
    StatusRuntimeException cause = new StatusRuntimeException(Status.DEADLINE_EXCEEDED);
    RuntimeException e = GrpcFactStore.wrapRetryable(cause);
    assertTrue(e instanceof StatusRuntimeException);
    assertSame(e, cause);
  }

  @Test
  void testWrapRetryable() {
    StatusRuntimeException cause = new StatusRuntimeException(Status.UNAVAILABLE);
    RuntimeException e = GrpcFactStore.wrapRetryable(cause);
    assertTrue(e instanceof RetryableException);
    assertSame(e.getCause(), cause);
  }

  @Test
  void testCancelIsPropagated() {
    ClientCall call = mock(ClientCall.class);
    uut.cancel(call);
    verify(call).cancel(any(), any());
  }

  @Test
  void testCancelIsNotRetryable() {
    ClientCall<MSG_SubscriptionRequest, MSG_Notification> call = mock(ClientCall.class);
    doThrow(StatusRuntimeException.class).when(call).cancel(any(), any());
    try {
      uut.cancel(call);
      fail();
    } catch (Throwable e) {
      assertTrue(e instanceof StatusRuntimeException);
      assertFalse(e instanceof RetryableException);
    }
  }

  @Test
  void testSubscribeNullParameters() {
    expectNPE(() -> uut.subscribe(null, mock(FactObserver.class)));
    expectNPE(() -> uut.subscribe(mock(SubscriptionRequestTO.class), null));
    expectNPE(() -> uut.subscribe(null, null));
  }

  @Test
  void testSerialOfNullParameters() {
    expectNPE(() -> uut.serialOf(null));
  }

  @Test
  void testInvalidate() {
    assertThrows(NullPointerException.class, () -> uut.invalidate(null));

    {
      UUID id = new UUID(0, 1);
      StateToken token = new StateToken(id);
      uut.invalidate(token);
      verify(blockingStub).invalidate(eq(conv.toProto(id)));
    }

    {
      when(blockingStub.invalidate(any()))
          .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));

      UUID id = new UUID(0, 1);
      StateToken token = new StateToken(id);
      try {
        uut.invalidate(token);
        fail();
      } catch (RetryableException expected) {
      }
    }
  }

  @Test
  void testStateForPositive() {

    UUID id = new UUID(0, 1);
    StateForRequest req = new StateForRequest(Lists.emptyList(), "foo");
    when(blockingStub.stateFor(any())).thenReturn(conv.toProto(id));
    val list = Arrays.asList(FactSpec.ns("foo").aggId(id));
    uut.stateFor(list);
    verify(blockingStub).stateForSpecsJson(conv.toProtoFactSpecs(list));
  }

  @Test
  void testStateForNegative() {
    when(blockingStub.stateForSpecsJson(any()))
        .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));
    try {
      uut.stateFor(Lists.emptyList());
      fail();
    } catch (RetryableException expected) {
    }
  }

  @Test
  void testPublishIfUnchangedPositive() {

    UUID id = new UUID(0, 1);
    ConditionalPublishRequest req = new ConditionalPublishRequest(Lists.emptyList(), id);
    when(blockingStub.publishConditional(any())).thenReturn(conv.toProto(true));

    boolean publishIfUnchanged =
        uut.publishIfUnchanged(Lists.emptyList(), Optional.of(new StateToken(id)));
    assertThat(publishIfUnchanged).isTrue();

    verify(blockingStub).publishConditional(conv.toProto(req));
  }

  @Test
  void testPublishIfUnchangedNegative() {

    UUID id = new UUID(0, 1);
    when(blockingStub.publishConditional(any()))
        .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));
    try {
      uut.publishIfUnchanged(Lists.emptyList(), Optional.of(new StateToken(id)));
      fail();
    } catch (RetryableException expected) {
    }
  }

  @Test
  void testSubscribe() {
    assertThrows(
        NullPointerException.class, () -> uut.subscribe(mock(SubscriptionRequestTO.class), null));
    assertThrows(NullPointerException.class, () -> uut.subscribe(null, null));
    assertThrows(NullPointerException.class, () -> uut.subscribe(null, mock(FactObserver.class)));
  }

  @Test
  void testCredentialsWrongFormat() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new GrpcFactStore(mock(Channel.class), Optional.ofNullable("xyz")));

    assertThrows(
        IllegalArgumentException.class,
        () -> new GrpcFactStore(mock(Channel.class), Optional.ofNullable("x:y:z")));

    assertThat(new GrpcFactStore(mock(Channel.class), Optional.ofNullable("xyz:abc"))).isNotNull();
  }

  @Test
  void testCredentialsRightFormat() {
    assertThat(new GrpcFactStore(mock(Channel.class), Optional.ofNullable("xyz:abc"))).isNotNull();
  }

  @Test
  public void testCurrentTime() {
    long l = 123L;
    when(blockingStub.currentTime(conv.empty())).thenReturn(conv.toProto(l));
    Long t = uut.currentTime();
    assertEquals(t, l);
  }

  @Test
  void testCurrentTimePropagatesRetryableExceptionOnUnavailableStatus() {
    when(blockingStub.currentTime(any())).thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));
    assertThrows(RetryableException.class, () -> uut.currentTime());
  }

  @Test
  void getSnapshotEmpty() {
    SnapshotId id = SnapshotId.of("foo", UUID.randomUUID());
    when(blockingStub.getSnapshot(eq(conv.toProto(id))))
        .thenReturn(conv.toProtoSnapshot(Optional.empty()));
    assertThat(uut.getSnapshot(id)).isEmpty();
  }

  @Test
  void getSnapshotException() {
    SnapshotId id = SnapshotId.of("foo", UUID.randomUUID());
    when(blockingStub.getSnapshot(eq(conv.toProto(id))))
        .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));

    assertThatThrownBy(() -> uut.getSnapshot(id)).isInstanceOf(RetryableException.class);
  }

  @Test
  void getSnapshot() {
    SnapshotId id = SnapshotId.of("foo", UUID.randomUUID());
    val snap = new Snapshot(id, UUID.randomUUID(), "".getBytes(), false);
    when(blockingStub.getSnapshot(eq(conv.toProto(id))))
        .thenReturn(conv.toProtoSnapshot(Optional.of(snap)));

    assertThat(uut.getSnapshot(id)).isPresent().contains(snap);
  }

  @Test
  void setSnapshotException() {
    SnapshotId id = SnapshotId.of("foo", UUID.randomUUID());
    val snap = new Snapshot(id, UUID.randomUUID(), "".getBytes(), false);
    when(blockingStub.setSnapshot(eq(conv.toProto(snap))))
        .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));

    assertThatThrownBy(() -> uut.setSnapshot(snap)).isInstanceOf(RetryableException.class);
  }

  @Test
  void setSnapshot() {
    SnapshotId id = SnapshotId.of("foo", UUID.randomUUID());
    val snap = new Snapshot(id, UUID.randomUUID(), "".getBytes(), false);
    when(blockingStub.setSnapshot(eq(conv.toProto(snap)))).thenReturn(conv.empty());

    uut.setSnapshot(snap);

    verify(blockingStub).setSnapshot(conv.toProto(snap));
  }

  @Test
  void clearSnapshotException() {
    SnapshotId id = SnapshotId.of("foo", UUID.randomUUID());
    when(blockingStub.clearSnapshot(eq(conv.toProto(id))))
        .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));

    assertThatThrownBy(() -> uut.clearSnapshot(id)).isInstanceOf(RetryableException.class);
  }

  @Test
  void clearSnapshot() {
    SnapshotId id = SnapshotId.of("foo", UUID.randomUUID());
    when(blockingStub.clearSnapshot(eq(conv.toProto(id)))).thenReturn(conv.empty());

    uut.clearSnapshot(id);

    verify(blockingStub).clearSnapshot(conv.toProto(id));
  }

  @Test
  void testAddClientIdToMetaIfExists() {
    Metadata meta = mock(Metadata.class);
    uut =
        new GrpcFactStore(
            mock(Channel.class),
            Optional.of("foo:bar"),
            new FactCastGrpcClientProperties(),
            "gurke");

    uut.addClientIdTo(meta);

    verify(meta).put(same(Headers.CLIENT_ID), eq("gurke"));
  }

  @Test
  void testAddClientIdToMetaDoesNotUseNull() {
    Metadata meta = mock(Metadata.class);
    uut = new GrpcFactStore(mock(Channel.class), Optional.of("foo:bar"));

    uut.addClientIdTo(meta);

    verifyNoInteractions(meta);
  }

  @Nested
  class RunAndHandle {
    @Mock private @NonNull Runnable block;

    @Test
    void skipsNonSRE() {
      RuntimeException damn = new RuntimeException("damn");
      doThrow(damn).when(block).run();
      assertThatThrownBy(
              () -> {
                underTest.runAndHandle(block);
              })
          .isSameAs(damn);
    }

    @Test
    void happyPath() {
      underTest.runAndHandle(block);
      verify(block).run();
    }

    @Test
    void translatesSRE() {

      String msg = "wrong";
      val e = new FactValidationException(msg);
      val metadata = new Metadata();
      metadata.put(
          Metadata.Key.of("msg-bin", Metadata.BINARY_BYTE_MARSHALLER), e.getMessage().getBytes());
      metadata.put(
          Metadata.Key.of("exc-bin", Metadata.BINARY_BYTE_MARSHALLER),
          e.getClass().getName().getBytes());

      doThrow(new StatusRuntimeException(Status.UNKNOWN.withDescription("crap"), metadata))
          .when(block)
          .run();
      assertThatThrownBy(
              () -> {
                underTest.runAndHandle(block);
              })
          .isNotSameAs(e)
          .isInstanceOf(FactValidationException.class)
          .extracting(Throwable::getMessage)
          .isEqualTo(msg);
    }
  }

  @Nested
  class CallAndHandle {
    @Mock private @NonNull Callable<?> block;

    @Test
    void skipsNonSRE() throws Exception {
      RuntimeException damn = new RuntimeException("damn");
      when(block.call()).thenThrow(damn);
      assertThatThrownBy(
              () -> {
                underTest.callAndHandle(block);
              })
          .isSameAs(damn);
    }

    @Test
    void happyPath() throws Exception {
      underTest.callAndHandle(block);
      verify(block).call();
    }

    @Test
    void translatesSRE() throws Exception {

      String msg = "wrong";
      val e = new FactValidationException(msg);
      val metadata = new Metadata();
      metadata.put(
          Metadata.Key.of("msg-bin", Metadata.BINARY_BYTE_MARSHALLER), e.getMessage().getBytes());
      metadata.put(
          Metadata.Key.of("exc-bin", Metadata.BINARY_BYTE_MARSHALLER),
          e.getClass().getName().getBytes());

      when(block.call())
          .thenThrow(new StatusRuntimeException(Status.UNKNOWN.withDescription("crap"), metadata));

      assertThatThrownBy(
              () -> {
                underTest.callAndHandle(block);
              })
          .isNotSameAs(e)
          .isInstanceOf(FactValidationException.class)
          .extracting(Throwable::getMessage)
          .isEqualTo(msg);
    }
  }
}
