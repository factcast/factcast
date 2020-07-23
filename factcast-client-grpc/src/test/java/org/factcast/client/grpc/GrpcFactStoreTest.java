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
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;

import org.assertj.core.util.Lists;
import org.factcast.core.Fact;
import org.factcast.core.store.RetryableException;
import org.factcast.core.store.StateToken;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.core.subscription.observer.FactObserver;
import org.factcast.grpc.api.ConditionalPublishRequest;
import org.factcast.grpc.api.StateForRequest;
import org.factcast.grpc.api.conv.ProtoConverter;
import org.factcast.grpc.api.conv.ProtocolVersion;
import org.factcast.grpc.api.conv.ServerConfig;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_Empty;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_Facts;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_Notification;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_OptionalFact;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_SubscriptionRequest;
import org.factcast.grpc.api.gen.RemoteFactStoreGrpc.RemoteFactStoreBlockingStub;
import org.factcast.grpc.api.gen.RemoteFactStoreGrpc.RemoteFactStoreStub;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.google.common.collect.Sets;

import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.val;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
@ExtendWith(MockitoExtension.class)
class GrpcFactStoreTest {

    @InjectMocks
    private GrpcFactStore uut;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private RemoteFactStoreBlockingStub blockingStub;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private RemoteFactStoreStub stub;

    @Mock
    private FactCastGrpcChannelFactory factory;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private SubscriptionRequestTO req;

    private final ProtoConverter conv = new ProtoConverter();

    @Captor
    private ArgumentCaptor<MSG_Facts> factsCap;

    @Mock
    public Optional<String> credentials;

    @Test
    void testPublish() {
        when(blockingStub.publish(factsCap.capture())).thenReturn(MSG_Empty.newBuilder().build());
        final TestFact fact = new TestFact();
        uut.publish(Collections.singletonList(fact));
        verify(blockingStub).publish(any());
        final MSG_Facts pfacts = factsCap.getValue();
        Fact published = conv.fromProto(pfacts.getFact(0));
        assertEquals(fact.id(), published.id());
    }

    @Test
    void configureCompressionChooseGzipIfAvail() {
        uut.configureCompression(" gzip,lz3,lz4, lz99");
        verify(stub).withCompression("gzip");
    }

    @Test
    void configureCompressionSkipCompression() {
        uut.configureCompression("zip,lz3,lz4, lz99");
        verifyNoMoreInteractions(stub);
    }

    @Test
    void fetchById() {
        final TestFact fact = new TestFact();
        val uuid = fact.id();
        val conv = new ProtoConverter();
        val id = conv.toProto(uuid);
        when(blockingStub.fetchById(eq(id))).thenReturn(MSG_OptionalFact.newBuilder()
                .setFact(conv.toProto(fact))
                .setPresent(true)
                .build());

        val result = uut.fetchById(fact.id());
        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(uuid);
    }

    @Test
    void fetchByIdAndVersion() {
        final TestFact fact = new TestFact();
        val uuid = fact.id();
        val conv = new ProtoConverter();
        val id = conv.toProto(uuid, 100);
        when(blockingStub.fetchByIdAndVersion(eq(id))).thenReturn(MSG_OptionalFact.newBuilder()
                .setFact(conv.toProto(fact))
                .setPresent(true)
                .build());

        val result = uut.fetchByIdAndVersion(fact.id(), 100);
        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(uuid);

    }

    static class SomeException extends RuntimeException {

        private static final long serialVersionUID = 1L;
    }

    @Test
    void testPublishPropagatesException() {
        when(blockingStub.publish(any())).thenThrow(new SomeException());
        assertThrows(SomeException.class, () -> uut.publish(Collections.singletonList(Fact.builder()
                .build("{}"))));
    }

    @Test
    void testPublishPropagatesRetryableExceptionOnUnavailableStatus() {
        when(blockingStub.publish(any())).thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));
        assertThrows(RetryableException.class, () -> uut.publish(Collections.singletonList(Fact
                .builder()
                .build("{}"))));
    }

    @Test
    void testCancelNotRetryableExceptionOnUnavailableStatus() {
        ClientCall<MSG_SubscriptionRequest, MSG_Notification> call = mock(ClientCall.class);
        doThrow(new StatusRuntimeException(Status.UNAVAILABLE)).when(call).cancel(any(), any());
        assertThrows(StatusRuntimeException.class, () -> uut.cancel(call));
    }

    @Test
    void testSerialOfPropagatesRetryableExceptionOnUnavailableStatus() {
        when(blockingStub.serialOf(any())).thenThrow(new StatusRuntimeException(
                Status.UNAVAILABLE));
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
        when(blockingStub.handshake(any())).thenThrow(new StatusRuntimeException(
                Status.UNAVAILABLE));
        assertThrows(RetryableException.class, () -> uut.initialize());
    }

    @Test
    void testEnumerateNamespacesPropagatesRetryableExceptionOnUnavailableStatus() {
        when(blockingStub.enumerateNamespaces(any())).thenThrow(new StatusRuntimeException(
                Status.UNAVAILABLE));
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
        when(blockingStub.enumerateTypes(any())).thenThrow(new StatusRuntimeException(
                Status.UNAVAILABLE));
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
        when(blockingStub.handshake(any()))
                .thenReturn(conv.toProto(ServerConfig.of(ProtocolVersion.of(1, 1, 0),
                        new HashMap<>())));
        uut.initialize();
    }

    @Test
    void testIncompatibleProtocolVersion() {
        when(blockingStub.handshake(any()))
                .thenReturn(conv.toProto(ServerConfig.of(ProtocolVersion.of(99, 0, 0),
                        new HashMap<>())));
        Assertions.assertThrows(IncompatibleProtocolVersions.class, () -> uut.initialize());
    }

    @Test
    void testInitializationExecutesOnlyOnce() {
        when(blockingStub.handshake(any()))
                .thenReturn(conv.toProto(ServerConfig.of(ProtocolVersion.of(1, 1, 0),
                        new HashMap<>())));
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
    void testAfterSingletonsInstantiatedCallsInit() {
        uut = spy(uut);
        when(blockingStub.handshake(any()))
                .thenReturn(conv.toProto(ServerConfig.of(ProtocolVersion.of(1, 999, 0),
                        new HashMap<>())));

        uut.afterSingletonsInstantiated();
        verify(uut).initialize();
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
    //
    // @Test
    // public void
    // testConfigureCompressionGZIPDisabledWhenServerReturnsNullCapability()
    // throws Exception {
    // uut.serverProperties(Maps.newHashMap(Capabilities.CODECS.toString(),
    // null));
    // assertFalse(uut.configureCompression(Capabilities.CODEC_GZIP));
    // }
    //
    // @Test
    // public void
    // testConfigureCompressionGZIPDisabledWhenServerReturnsFalseCapability()
    // throws Exception {
    // uut.serverProperties(Maps.newHashMap(Capabilities.CODEC_GZIP.toString(),
    // "false"));
    // assertFalse(uut.configureCompression(Capabilities.CODEC_GZIP));
    // }
    //
    // @Test
    // public void
    // testConfigureCompressionGZIPEnabledWhenServerReturnsCapability() throws
    // Exception {
    // uut.serverProperties(Maps.newHashMap(Capabilities.CODEC_GZIP.toString(),
    // "true"));
    // assertTrue(uut.configureCompression(Capabilities.CODEC_GZIP));
    // }
    //
    // @Test
    // public void testConfigureCompressionGZIP() throws Exception {
    // uut = spy(uut);
    // uut.serverProperties(new HashMap<>());
    // uut.configureCompression();
    // verify(uut).configureCompression(Capabilities.CODEC_GZIP);
    // }
    //
    // @Test
    // public void testConfigureCompressionLZ4() throws Exception {
    // uut = spy(uut);
    // uut.serverProperties(new HashMap<>());
    // when(uut.configureCompression(Capabilities.CODEC_LZ4)).thenReturn(true);
    // uut.configureCompression();
    // verify(uut, never()).configureCompression(Capabilities.CODEC_GZIP);
    // }

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
            when(blockingStub.invalidate(any())).thenThrow(
                    new StatusRuntimeException(
                            Status.UNAVAILABLE));

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

        uut.stateFor(Lists.emptyList(), Optional.of("foo"));
        verify(blockingStub).stateFor(conv.toProto(req));
    }

    @Test
    void testStateForNegative() {
        when(blockingStub.stateFor(any())).thenThrow(
                new StatusRuntimeException(
                        Status.UNAVAILABLE));
        try {
            uut.stateFor(Lists.emptyList(), Optional.of("foo"));
            fail();
        } catch (RetryableException expected) {
        }
    }

    @Test
    void testPublishIfUnchangedPositive() {

        UUID id = new UUID(0, 1);
        ConditionalPublishRequest req = new ConditionalPublishRequest(Lists.emptyList(), id);
        when(blockingStub.publishConditional(any())).thenReturn(conv.toProto(true));

        boolean publishIfUnchanged = uut.publishIfUnchanged(Lists.emptyList(), Optional.of(
                new StateToken(id)));
        assertThat(publishIfUnchanged).isTrue();

        verify(blockingStub).publishConditional(conv.toProto(req));

    }

    @Test
    void testPublishIfUnchangedNegative() {

        UUID id = new UUID(0, 1);
        when(blockingStub.publishConditional(any())).thenThrow(
                new StatusRuntimeException(
                        Status.UNAVAILABLE));
        try {
            uut.publishIfUnchanged(Lists.emptyList(), Optional.of(
                    new StateToken(id)));
            fail();
        } catch (RetryableException expected) {
        }
    }

    @Test
    void testSubscribe() {
        assertThrows(NullPointerException.class, () -> uut.subscribe(mock(
                SubscriptionRequestTO.class), null));
        assertThrows(NullPointerException.class, () -> uut.subscribe(null, null));
        assertThrows(NullPointerException.class, () -> uut.subscribe(null, mock(
                FactObserver.class)));

    }

    @Test
    void testCredentialsWrongFormat() {
        assertThrows(IllegalArgumentException.class, () -> new GrpcFactStore(mock(Channel.class),
                Optional.ofNullable("xyz")));

        assertThrows(IllegalArgumentException.class, () -> new GrpcFactStore(mock(Channel.class),
                Optional.ofNullable("x:y:z")));

        assertThat(new GrpcFactStore(mock(Channel.class), Optional.ofNullable("xyz:abc")))
                .isNotNull();

    }

    @Test
    void testCredentialsRightFormat() {
        assertThat(new GrpcFactStore(mock(Channel.class), Optional.ofNullable("xyz:abc")))
                .isNotNull();
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
        when(blockingStub.enumerateNamespaces(any())).thenThrow(new StatusRuntimeException(
                Status.UNAVAILABLE));
        assertThrows(RetryableException.class, () -> uut.enumerateNamespaces());
    }
}
