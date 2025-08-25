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
import static org.factcast.client.grpc.GrpcFactStore.PROTOCOL_VERSION;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import io.grpc.*;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import lombok.NonNull;
import org.assertj.core.api.Assertions;
import org.factcast.core.DuplicateFactException;
import org.factcast.core.Fact;
import org.factcast.core.FactValidationException;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.store.RetryableException;
import org.factcast.core.store.StateToken;
import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.SubscriptionRequest;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.core.subscription.observer.FactObserver;
import org.factcast.grpc.api.Capabilities;
import org.factcast.grpc.api.ConditionalPublishRequest;
import org.factcast.grpc.api.Headers;
import org.factcast.grpc.api.conv.ProtoConverter;
import org.factcast.grpc.api.conv.ProtocolVersion;
import org.factcast.grpc.api.conv.ServerConfig;
import org.factcast.grpc.api.gen.FactStoreProto;
import org.factcast.grpc.api.gen.FactStoreProto.*;
import org.factcast.grpc.api.gen.RemoteFactStoreGrpc;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GrpcFactStoreTest {

  @Mock(strictness = Mock.Strictness.LENIENT)
  FactCastGrpcClientProperties properties;

  @Mock(strictness = Mock.Strictness.LENIENT)
  GrpcStubs grpcStubs;

  @Captor ArgumentCaptor<MSG_Facts> factsCap;

  ProtoConverter conv = new ProtoConverter();

  final FactCastGrpcClientProperties.ResilienceConfiguration resilienceConfig =
      new FactCastGrpcClientProperties.ResilienceConfiguration();

  @Mock(strictness = Mock.Strictness.LENIENT)
  RemoteFactStoreGrpc.RemoteFactStoreBlockingStub uncompressedBlockingStub;

  @Mock(strictness = Mock.Strictness.LENIENT)
  RemoteFactStoreGrpc.RemoteFactStoreBlockingStub blockingStub;

  @Mock(strictness = Mock.Strictness.LENIENT)
  RemoteFactStoreGrpc.RemoteFactStoreStub nonBlockingStub;

  GrpcFactStore uut;

  @BeforeEach
  public void setup() {
    when(properties.getResilience()).thenReturn(resilienceConfig);
    resilienceConfig.setEnabled(false);

    uut = new GrpcFactStore(grpcStubs, properties);

    when(grpcStubs.uncompressedBlocking(any())).thenReturn(uncompressedBlockingStub);
    when(grpcStubs.uncompressedBlocking()).thenReturn(uncompressedBlockingStub);
    when(grpcStubs.blocking()).thenReturn(blockingStub);
    when(grpcStubs.nonBlocking()).thenReturn(nonBlockingStub);
    when(uncompressedBlockingStub.handshake(any()))
        .thenReturn(conv.toProto(ServerConfig.of(PROTOCOL_VERSION, new HashMap<>())));
  }

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
    Map<String, String> serverProps = new HashMap<>();
    serverProps.put(Capabilities.CODECS.toString(), " gzip,lz3,lz4, lz99");
    when(uncompressedBlockingStub.handshake(any()))
        .thenReturn(conv.toProto(ServerConfig.of(PROTOCOL_VERSION, serverProps)));
    uut.reset();
    uut.initializeIfNecessary();
    verify(grpcStubs).compression("gzip");
  }

  @Test
  void configureCompressionSkipCompression() {
    Map<String, String> serverProps = new HashMap<>();
    serverProps.put(Capabilities.CODECS.toString(), "zip,lz3,lz4, lz99");
    when(uncompressedBlockingStub.handshake(any()))
        .thenReturn(conv.toProto(ServerConfig.of(PROTOCOL_VERSION, serverProps)));
    uut.reset();
    uut.initializeIfNecessary();
    verify(grpcStubs, never()).compression(anyString());
  }

  @Test
  void configureWithFastForwardEnabled() {
    when(properties.isEnableFastForward()).thenReturn(true);
    Metadata meta = GrpcFactStore.prepareMetaData(properties, "client-id");
    assertThat(meta.containsKey(Headers.FAST_FORWARD)).isTrue();
  }

  @Test
  void configureWithFastForwardDisabled() {
    when(properties.isEnableFastForward()).thenReturn(false);
    Metadata meta = GrpcFactStore.prepareMetaData(properties, "client-id");
    assertThat(meta.containsKey(Headers.FAST_FORWARD)).isFalse();
  }

  @Test
  void configureWithBatchSize1() {
    when(properties.getMaxInboundMessageSize()).thenReturn(1777);
    Metadata meta = GrpcFactStore.prepareMetaData(properties, "client-id");
    assertThat(meta.get(Headers.CLIENT_MAX_INBOUND_MESSAGE_SIZE)).isEqualTo("1777");
  }

  @Test
  void fetchById() {
    TestFact fact = new TestFact();
    UUID uuid = fact.id();
    conv = new ProtoConverter();
    @NonNull FactStoreProto.MSG_UUID id = conv.toProto(uuid);
    when(blockingStub.fetchById(id))
        .thenReturn(
            MSG_OptionalFact.newBuilder().setFact(conv.toProto(fact)).setPresent(true).build());

    Optional<Fact> result = uut.fetchById(fact.id());
    assertThat(result).isPresent();
    assertThat(result.get().id()).isEqualTo(uuid);
  }

  @Test
  void fetchByIdAndVersion() {
    TestFact fact = new TestFact();
    UUID uuid = fact.id();
    conv = new ProtoConverter();
    @NonNull FactStoreProto.MSG_UUID_AND_VERSION id = conv.toProto(uuid, 100);
    when(blockingStub.fetchByIdAndVersion(eq(id)))
        .thenReturn(
            MSG_OptionalFact.newBuilder().setFact(conv.toProto(fact)).setPresent(true).build());

    Optional<Fact> result = uut.fetchByIdAndVersion(fact.id(), 100);
    assertThat(result).isPresent();
    assertThat(result.get().id()).isEqualTo(uuid);
  }

  @Test
  void fetchByIdThrowsRetryable() {
    TestFact fact = new TestFact();
    UUID uuid = fact.id();
    @NonNull FactStoreProto.MSG_UUID id = conv.toProto(uuid);
    when(blockingStub.fetchById(eq(id))).thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));

    assertThatThrownBy(() -> uut.fetchById(fact.id())).isInstanceOf(RetryableException.class);
  }

  @Test
  void fetchByIdAndVersionThrowsRetryable() {
    TestFact fact = new TestFact();
    UUID uuid = fact.id();
    @NonNull FactStoreProto.MSG_UUID_AND_VERSION id = conv.toProto(uuid, 100);
    when(blockingStub.fetchByIdAndVersion(eq(id)))
        .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));

    assertThatThrownBy(() -> uut.fetchByIdAndVersion(fact.id(), 100))
        .isInstanceOf(RetryableException.class);
  }

  static class SomeException extends RuntimeException {
    private static final long serialVersionUID = 1L;
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
  void testPublishPropagatesDuplicatedFactExceptionWhenNotIgnoring() {
    when(properties.isIgnoreDuplicateFacts()).thenReturn(false);
    when(blockingStub.publish(any())).thenThrow(new DuplicateFactException("test"));
    assertThrows(
        DuplicateFactException.class,
        () -> uut.publish(Collections.singletonList(Fact.builder().build("{}"))));
  }

  @Test
  void testPublishSkipsSingleDuplicatedFactWhenIgnoring() {
    when(properties.isIgnoreDuplicateFacts()).thenReturn(true);
    when(blockingStub.publish(any())).thenThrow(new DuplicateFactException("test"));
    assertDoesNotThrow(() -> uut.publish(Collections.singletonList(Fact.builder().build("{}"))));
    // does not publish again the duplicated fact
    verify(blockingStub, times(1)).publish(any());
  }

  @Test
  void testPublishRetriesDuplicatedFactsWhenIgnoring() {
    UUID duplicatedFactId = UUID.randomUUID();
    when(properties.isIgnoreDuplicateFacts()).thenReturn(true);
    when(blockingStub.publish(
            argThat(
                facts ->
                    conv.fromProto(facts).stream()
                        .anyMatch(fact -> fact.id().equals(duplicatedFactId)))))
        .thenThrow(new DuplicateFactException("test"));
    assertDoesNotThrow(
        () ->
            uut.publish(
                Lists.newArrayList(
                    Fact.builder().id(UUID.randomUUID()).build("{}"),
                    Fact.builder().id(duplicatedFactId).build("{}"),
                    Fact.builder().id(UUID.randomUUID()).build("{}"))));
    // does publish again every fact singularly
    verify(blockingStub, times(4)).publish(any());
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
  void testInitializePropagatesRetryableExceptionOnUnavailableStatus() {
    when(uncompressedBlockingStub.handshake(any()))
        .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));
    uut.reset();
    assertThrows(RetryableException.class, () -> uut.initializeIfNecessary());
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
  void testEnumerateVersionsPropagatesRetryableExceptionOnUnavailableStatus() {
    when(blockingStub.enumerateVersions(any()))
        .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));
    assertThrows(RetryableException.class, () -> uut.enumerateVersions("ns", "type"));
  }

  @Test
  void testEnumerateVersions() {
    HashSet<Integer> types = Sets.newHashSet(1, 2);
    when(blockingStub.enumerateVersions(any())).thenReturn(conv.toProtoIntSet(types));
    Set<Integer> enumerateVersions = uut.enumerateVersions("ns", "type");
    assertEquals(types, enumerateVersions);
    assertNotSame(types, enumerateVersions);
  }

  @Test
  void testCompatibleProtocolVersion() {
    when(blockingStub.withInterceptors(any())).thenReturn(blockingStub);
    when(blockingStub.handshake(any()))
        .thenReturn(conv.toProto(ServerConfig.of(PROTOCOL_VERSION, new HashMap<>())));
    uut.reset();
    uut.initializeIfNecessary();
  }

  @Test
  void testIncompatibleProtocolVersion() {
    when(uncompressedBlockingStub.handshake(any()))
        .thenReturn(conv.toProto(ServerConfig.of(ProtocolVersion.of(99, 0, 0), new HashMap<>())));
    uut.reset();
    assertThrows(IncompatibleProtocolVersions.class, () -> uut.initializeIfNecessary());
  }

  @Test
  void testInitializationExecutesHandshakeOnlyOnce() {
    when(blockingStub.withInterceptors(any())).thenReturn(blockingStub);
    when(blockingStub.handshake(any()))
        .thenReturn(conv.toProto(ServerConfig.of(PROTOCOL_VERSION, new HashMap<>())));
    uut.reset();
    uut.initializeIfNecessary();
    uut.initializeIfNecessary();
    verify(grpcStubs)
        .uncompressedBlocking(argThat(d -> d.isBefore(Deadline.after(10, TimeUnit.SECONDS))));
    verify(uncompressedBlockingStub, times(1)).handshake(any());
  }

  @Test
  void testWrapRetryable_nonRetryable() {
    StatusRuntimeException cause = new StatusRuntimeException(Status.ALREADY_EXISTS);
    RuntimeException e = ClientExceptionHelper.from(cause);
    assertInstanceOf(StatusRuntimeException.class, e);
    assertSame(e, cause);
  }

  @Test
  void testWrapRetryable() {
    StatusRuntimeException cause = new StatusRuntimeException(Status.UNAVAILABLE);
    RuntimeException e = ClientExceptionHelper.from(cause);
    assertInstanceOf(RetryableException.class, e);
    assertSame(e.getCause(), cause);
  }

  @Test
  void testCancelIsPropagated() {
    ClientCall<MSG_SubscriptionRequest, MSG_Notification> call = mock(ClientCall.class);
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
      assertInstanceOf(StatusRuntimeException.class, e);
      assertFalse(e instanceof RetryableException);
    }
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
    when(blockingStub.stateForSpecsJson(any())).thenReturn(conv.toProto(id));
    List<FactSpec> list = Collections.singletonList(FactSpec.ns("foo").aggId(id));
    uut.stateFor(list);
    verify(blockingStub).stateForSpecsJson(conv.toProtoFactSpecs(list));
  }

  @Test
  void testStateForNegative() {
    when(blockingStub.stateForSpecsJson(any()))
        .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));
    try {
      uut.stateFor(Collections.emptyList());
      fail();
    } catch (RetryableException expected) {
    }
  }

  @Test
  void testCurrentStateForPositive() {
    uut.fastStateToken(true);
    UUID id = new UUID(0, 1);

    when(blockingStub.currentStateForSpecsJson(any())).thenReturn(conv.toProto(id));
    List<FactSpec> list = Collections.singletonList(FactSpec.ns("foo").aggId(id));
    uut.currentStateFor(list);
    verify(blockingStub).currentStateForSpecsJson(conv.toProtoFactSpecs(list));
  }

  @Test
  void testCurrentStateForNegative() {
    uut.fastStateToken(true);
    when(blockingStub.currentStateForSpecsJson(any()))
        .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));
    try {
      uut.currentStateFor(Collections.emptyList());
      fail();
    } catch (RetryableException expected) {
    }
  }

  @Test
  void testCurrentStateForWithFastStateTokenDisabled() {
    uut.fastStateToken(false);
    UUID id = new UUID(0, 1);
    when(blockingStub.stateForSpecsJson(any())).thenReturn(conv.toProto(id));
    List<FactSpec> list = Collections.singletonList(FactSpec.ns("foo").aggId(id));
    uut.currentStateFor(list);
    verify(blockingStub).stateForSpecsJson(conv.toProtoFactSpecs(list));
  }

  @Test
  void testPublishIfUnchangedPositive() {

    UUID id = new UUID(0, 1);
    ConditionalPublishRequest req = new ConditionalPublishRequest(Collections.emptyList(), id);
    when(blockingStub.publishConditional(any())).thenReturn(conv.toProto(true));

    boolean publishIfUnchanged =
        uut.publishIfUnchanged(Collections.emptyList(), Optional.of(new StateToken(id)));
    assertThat(publishIfUnchanged).isTrue();

    verify(blockingStub).publishConditional(conv.toProto(req));
  }

  @Test
  void testPublishIfUnchangedNegative() {

    UUID id = new UUID(0, 1);
    when(blockingStub.publishConditional(any()))
        .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));
    try {
      uut.publishIfUnchanged(Collections.emptyList(), Optional.of(new StateToken(id)));
      fail();
    } catch (RetryableException expected) {
    }
  }

  @Test
  void testInternalSubscribe() {
    assertThrows(
        NullPointerException.class, () -> uut.subscribe(mock(SubscriptionRequestTO.class), null));
    assertThrows(NullPointerException.class, () -> uut.internalSubscribe(null, null));
    assertThrows(
        NullPointerException.class, () -> uut.internalSubscribe(null, mock(FactObserver.class)));
  }

  @Test
  void testSubscribeWithoutResilience() {
    Channel channel = mock(Channel.class);
    when(nonBlockingStub.getCallOptions()).thenReturn(CallOptions.DEFAULT);
    when(nonBlockingStub.getChannel()).thenReturn(channel);
    when(channel.newCall(any(), any())).thenReturn(mock(ClientCall.class));

    resilienceConfig.setEnabled(false);
    SubscriptionRequestTO req =
        new SubscriptionRequestTO(SubscriptionRequest.catchup(FactSpec.ns("foo")).fromScratch());
    Subscription s = uut.subscribe(req, elements -> {});

    assertThat(s).isInstanceOf(Subscription.class).isNotInstanceOf(ResilientGrpcSubscription.class);
  }

  @Test
  void testSubscribeWithResilience() {
    resilienceConfig.setEnabled(true);
    SubscriptionRequestTO req =
        new SubscriptionRequestTO(SubscriptionRequest.catchup(FactSpec.ns("foo")).fromScratch());
    Channel channel = mock(Channel.class);
    when(nonBlockingStub.getCallOptions()).thenReturn(CallOptions.DEFAULT);
    when(nonBlockingStub.getChannel()).thenReturn(channel);
    when(channel.newCall(any(), any())).thenReturn(mock(ClientCall.class));
    Subscription s = uut.subscribe(req, element -> {});

    assertThat(s).isInstanceOf(ResilientGrpcSubscription.class);
  }

  @Nested
  @SuppressWarnings("java:S5778")
  class Credentials {
    @Test
    void testLegacyCredentialsWrongFormat() {
      final FactCastGrpcClientProperties props = new FactCastGrpcClientProperties();
      Optional<String> creds1 = Optional.of("xyz");
      assertThrows(
          IllegalArgumentException.class, () -> GrpcFactStore.configureCredentials(creds1, props));
      Optional<String> creds2 = Optional.of("x:y:z");
      assertThrows(
          IllegalArgumentException.class, () -> GrpcFactStore.configureCredentials(creds2, props));
      Optional<String> creds3 = Optional.of("ab:cd");
      assertDoesNotThrow(() -> GrpcFactStore.configureCredentials(creds3, props));
    }

    @Test
    void testLegacyCredentialsRightFormat() {
      final FactCastGrpcClientProperties props = new FactCastGrpcClientProperties();
      Optional<String> creds = Optional.of("xyz:abc");
      assertThat(GrpcFactStore.configureCredentials(creds, props)).isNotNull();
    }

    @Test
    void testNewCredentials() {
      final FactCastGrpcClientProperties props = new FactCastGrpcClientProperties();
      props.setUser("foo");
      props.setPassword("bar");
      assertThat(GrpcFactStore.configureCredentials(Optional.empty(), props)).isNotNull();
    }

    @Test
    void testNewCredentialsNoPassword() {
      final FactCastGrpcClientProperties props = new FactCastGrpcClientProperties();
      props.setUser("user");
      assertThrows(
          IllegalArgumentException.class,
          () -> GrpcFactStore.configureCredentials(Optional.empty(), props));
    }

    @Test
    void testNewCredentialsEmptyPassword() {
      final FactCastGrpcClientProperties props = new FactCastGrpcClientProperties();
      props.setUser("user");
      props.setPassword("");
      Optional<String> empty = Optional.empty();
      assertThrows(
          IllegalArgumentException.class, () -> GrpcFactStore.configureCredentials(empty, props));
    }

    @Test
    void testNewCredentialsNoUsername() {
      final FactCastGrpcClientProperties props = new FactCastGrpcClientProperties();
      props.setPassword("password");
      Optional<String> empty = Optional.empty();
      assertThrows(
          IllegalArgumentException.class, () -> GrpcFactStore.configureCredentials(empty, props));
    }

    @Test
    void testNewCredentialsEmptyUsername() {
      final FactCastGrpcClientProperties props = new FactCastGrpcClientProperties();
      props.setUser("");
      props.setPassword("password");
      Optional<String> empty = Optional.empty();
      assertThrows(
          IllegalArgumentException.class, () -> GrpcFactStore.configureCredentials(empty, props));
    }

    @Test
    void testLegacyCredentialsEmptyUsername() {
      final FactCastGrpcClientProperties props = new FactCastGrpcClientProperties();
      Optional<String> creds = Optional.of(":abc");
      assertThrows(
          IllegalArgumentException.class, () -> GrpcFactStore.configureCredentials(creds, props));
    }

    @Test
    void testLegacyCredentialsEmptyPassword() {
      final FactCastGrpcClientProperties props = new FactCastGrpcClientProperties();
      Optional<String> creds = Optional.of("xyz:");
      assertThrows(
          IllegalArgumentException.class, () -> GrpcFactStore.configureCredentials(creds, props));
    }

    @Test
    void testNoCredentials() {
      final FactCastGrpcClientProperties props = new FactCastGrpcClientProperties();
      Optional<String> creds = Optional.empty();
      assertThat(GrpcFactStore.configureCredentials(creds, props)).isNull();
    }
  }

  @Test
  void testCurrentTime() {
    long l = 123L;
    when(blockingStub.currentTime(conv.empty())).thenReturn(conv.toProtoTime(l));
    Long t = uut.currentTime();
    assertEquals(t, l);
  }

  @Test
  void testCurrentTimePropagatesRetryableExceptionOnUnavailableStatus() {
    when(blockingStub.currentTime(any())).thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));
    assertThrows(RetryableException.class, () -> uut.currentTime());
  }

  @Test
  void testAddClientIdToMeta() {
    Metadata meta = GrpcFactStore.prepareMetaData(new FactCastGrpcClientProperties(), "gurke");
    assertThat(meta.get(Headers.CLIENT_ID)).isEqualTo("gurke");
  }

  @Test
  void testNullClientIdToMeta() {
    Metadata meta = GrpcFactStore.prepareMetaData(new FactCastGrpcClientProperties(), null);
    assertThat(meta.get(Headers.CLIENT_ID)).isNull();
  }

  @Test
  void testAddClientVersionToMeta() {
    Metadata meta = GrpcFactStore.prepareMetaData(new FactCastGrpcClientProperties(), "gurke");
    assertThat(meta.get(Headers.CLIENT_VERSION)).isNotBlank();
  }

  @Nested
  class RunAndHandle {
    @Mock private @NonNull Runnable block;

    @Test
    void skipsNonSRE() {
      RuntimeException damn = new RuntimeException("damn");
      doThrow(damn).when(block).run();
      assertThatThrownBy(() -> uut.runAndHandle(block)).isSameAs(damn);
    }

    @Test
    void happyPath() {
      uut.runAndHandle(block);
      verify(block).run();
    }

    @Test
    void translatesSRE() {
      String msg = "wrong";
      FactValidationException e = new FactValidationException(msg);
      Metadata metadata = new Metadata();
      metadata.put(
          Metadata.Key.of("msg-bin", Metadata.BINARY_BYTE_MARSHALLER), e.getMessage().getBytes());
      metadata.put(
          Metadata.Key.of("exc-bin", Metadata.BINARY_BYTE_MARSHALLER),
          e.getClass().getName().getBytes());

      doThrow(new StatusRuntimeException(Status.UNKNOWN.withDescription("crap"), metadata))
          .when(block)
          .run();
      assertThatThrownBy(() -> uut.runAndHandle(block))
          .isNotSameAs(e)
          .isInstanceOf(FactValidationException.class)
          .extracting(Throwable::getMessage)
          .isEqualTo(msg);
    }
  }

  @Nested
  class CallAndHandle {
    @Mock private @NonNull Callable<?> block;
    @Mock private @NonNull Runnable runnable;

    @Test
    void skipsNonSRE() throws Exception {
      RuntimeException damn = new RuntimeException("damn");
      when(block.call()).thenThrow(damn);
      assertThatThrownBy(() -> uut.callAndHandle(block)).isSameAs(damn);
    }

    @Test
    void happyPath() throws Exception {
      uut.callAndHandle(block);
      verify(block).call();
    }

    @Test
    void retriesCall() throws Exception {
      resilienceConfig.setEnabled(true).setAttempts(100).setInterval(Duration.ofMillis(100));
      when(block.call()).thenThrow(new RetryableException(new IOException())).thenReturn(null);
      uut.callAndHandle(block);
      verify(uncompressedBlockingStub, times(2)).handshake(any());
      verify(block, times(2)).call();
    }

    @Test
    void retriesRun() {
      resilienceConfig.setEnabled(true).setAttempts(100).setInterval(Duration.ofMillis(100));
      doThrow(new RetryableException(new IOException())).doNothing().when(runnable).run();
      uut.runAndHandle(runnable);
      verify(uncompressedBlockingStub, times(2)).handshake(any());
      verify(runnable, times(2)).run();
    }

    @Test
    void translatesSRE() throws Exception {
      String msg = "wrong";
      FactValidationException e = new FactValidationException(msg);
      Metadata metadata = new Metadata();
      metadata.put(
          Metadata.Key.of("msg-bin", Metadata.BINARY_BYTE_MARSHALLER), e.getMessage().getBytes());
      metadata.put(
          Metadata.Key.of("exc-bin", Metadata.BINARY_BYTE_MARSHALLER),
          e.getClass().getName().getBytes());

      when(block.call())
          .thenThrow(new StatusRuntimeException(Status.UNKNOWN.withDescription("crap"), metadata));

      assertThatThrownBy(() -> uut.callAndHandle(block))
          .isNotSameAs(e)
          .isInstanceOf(FactValidationException.class)
          .extracting(Throwable::getMessage)
          .isEqualTo(msg);
    }
  }

  @Test
  void latestSerial() {
    MSG_Serial ser = conv.toProto(2L);
    when(blockingStub.latestSerial(any())).thenReturn(ser);
    Assertions.assertThat(uut.latestSerial()).isEqualTo(2);
  }

  @Test
  void lastSerialBefore() {
    LocalDate date = LocalDate.of(2003, 12, 24);
    MSG_Date msgDate = conv.toProto(date);
    when(blockingStub.lastSerialBefore(msgDate)).thenReturn(conv.toProto(2L));
    Assertions.assertThat(uut.lastSerialBefore(date)).isEqualTo(2);
  }

  @Test
  void firstSerialAfter() {
    LocalDate date = LocalDate.of(2003, 12, 24);
    MSG_Date msgDate = conv.toProto(date);
    when(blockingStub.firstSerialAfter(msgDate)).thenReturn(conv.toProto(2L));
    Assertions.assertThat(uut.firstSerialAfter(date)).isEqualTo(2);
  }

  @Test
  void fetchBySerial() {
    TestFact fact = new TestFact();
    long serial = 2L;
    when(blockingStub.fetchBySerial(conv.toProto(serial)))
        .thenReturn(
            MSG_OptionalFact.newBuilder().setFact(conv.toProto(fact)).setPresent(true).build());

    Optional<Fact> result = uut.fetchBySerial(serial);
    assertThat(result).isPresent();
  }

  @Test
  void initializationCreatesNewStubs() {
    when(blockingStub.currentTime(conv.empty())).thenReturn(conv.toProtoTime(123L));
    int nReInitializations = 100;
    for (int i = 0; i < nReInitializations; i++) {
      uut.reset();
      uut.initializeIfNecessary();
    }
    verify(grpcStubs, times(nReInitializations)).uncompressedBlocking(any());
    // should work after multiple re-initializations (issue #2868)
    uut.currentTime();
  }

  @Test
  void resetsInitializationFlag() {
    uut.reset();
    uut.initializeIfNecessary();
    uut.reset();
    uut.initializeIfNecessary();
    verify(uncompressedBlockingStub, times(2)).handshake(any());
  }

  @Test
  void throwsWhenProtocolVersionIsIncompatible() {
    ProtocolVersion v = ProtocolVersion.of(99, 98, 97);
    assertThatThrownBy(() -> GrpcFactStore.logProtocolVersion(v))
        .isInstanceOf(IncompatibleProtocolVersions.class);
  }

  @Test
  void doesNotThrowWhenProtocolVersionIsCompatible() {
    ProtocolVersion v =
        ProtocolVersion.of(PROTOCOL_VERSION.major(), PROTOCOL_VERSION.minor() + 1, 0);
    assertDoesNotThrow(() -> GrpcFactStore.logProtocolVersion(v));
  }

  @Test
  void doesNotThrowWhenProtocolVersionMatches() {
    ProtocolVersion v =
        ProtocolVersion.of(
            PROTOCOL_VERSION.major(), PROTOCOL_VERSION.minor(), PROTOCOL_VERSION.patch());
    assertDoesNotThrow(() -> GrpcFactStore.logProtocolVersion(v));
  }
}
