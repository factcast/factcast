/*
 * Copyright © 2018 Mercateo AG (http://www.mercateo.com)
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
import static org.mockito.Mockito.*;

import java.util.*;

import org.assertj.core.util.Lists;
import org.factcast.core.*;
import org.factcast.core.store.*;
import org.factcast.core.subscription.*;
import org.factcast.core.subscription.observer.*;
import org.factcast.grpc.api.*;
import org.factcast.grpc.api.conv.*;
import org.factcast.grpc.api.gen.FactStoreProto.*;
import org.factcast.grpc.api.gen.RemoteFactStoreGrpc.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.*;
import org.mockito.junit.jupiter.*;

import com.google.common.collect.Sets;

import io.grpc.*;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
@ExtendWith(MockitoExtension.class)
class GrpcFactStoreTest {

    @InjectMocks
    private GrpcFactStore uut;

    @Mock
    private RemoteFactStoreBlockingStub blockingStub;

    @Mock
    private RemoteFactStoreStub stub;

    @Mock
    private FactCastGrpcChannelFactory factory;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private SubscriptionRequestTO req;

    private ProtoConverter conv = new ProtoConverter();

    @Captor
    private ArgumentCaptor<MSG_Facts> factsCap;

    @Mock
    public Optional<String> credentials;

    @Test
    void testFetchByIdNotFound() {
        UUID id = UUID.randomUUID();
        when(blockingStub.fetchById(eq(conv.toProto(id)))).thenReturn(conv.toProto(Optional
                .empty()));
        Optional<Fact> fetchById = uut.fetchById(id);
        assertFalse(fetchById.isPresent());
    }

    @Test
    void testFetchByIdFound() {
        UUID id = UUID.randomUUID();
        when(blockingStub.fetchById(eq(conv.toProto(id))))
                .thenReturn(conv.toProto(Optional.of(Fact.builder().ns("test").build("{}"))));
        Optional<Fact> fetchById = uut.fetchById(id);
        assertTrue(fetchById.isPresent());
    }

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
    void testPublishNullParameter() {
        assertThrows(NullPointerException.class, () -> uut.publish(null));
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

    static class SomeException extends RuntimeException {

        private static final long serialVersionUID = 1L;
    }

    @Test
    void testPublishPropagatesException() {
        when(blockingStub.publish(any())).thenThrow(new SomeException());
        assertThrows(SomeException.class, () -> uut.publish(Collections.singletonList(Fact.builder().build("{}"))));
    }

    @Test
    void testFetchByIdPropagatesRetryableExceptionOnUnavailableStatus() {
        when(blockingStub.fetchById(any())).thenThrow(new StatusRuntimeException(
                Status.UNAVAILABLE));
        assertThrows(RetryableException.class, () -> uut.fetchById(UUID.randomUUID()));
    }

    @Test
    void testPublishPropagatesRetryableExceptionOnUnavailableStatus() {
        when(blockingStub.publish(any())).thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));
        assertThrows(RetryableException.class, () -> uut.publish(Collections.singletonList(Fact.builder().build("{}"))));
    }

    @SuppressWarnings("unchecked")
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

    // @Test
    // void testConstruction() {
    // expectNPE(() -> new GrpcFactStore((Channel) null));
    // expectNPE(() -> new
    // GrpcFactStore(mock(RemoteFactStoreBlockingStub.class), null));
    // expectNPE(() -> new GrpcFactStore(null,
    // mock(RemoteFactStoreStub.class)));
    // expectNPE(() -> new GrpcFactStore(null, null));
    // }

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
    void testWrapRetryable_nonRetryable() throws Exception {
        StatusRuntimeException cause = new StatusRuntimeException(Status.DEADLINE_EXCEEDED);
        RuntimeException e = GrpcFactStore.wrapRetryable(cause);
        assertTrue(e instanceof StatusRuntimeException);
        assertSame(e, cause);
    }

    @Test
    void testWrapRetryable() throws Exception {
        StatusRuntimeException cause = new StatusRuntimeException(Status.UNAVAILABLE);
        RuntimeException e = GrpcFactStore.wrapRetryable(cause);
        assertTrue(e instanceof RetryableException);
        assertSame(e.getCause(), cause);
    }

    @Test
    void testCancelIsPropagated() throws Exception {
        ClientCall call = mock(ClientCall.class);
        uut.cancel(call);
        verify(call).cancel(any(), any());
    }

    @Test
    void testCancelIsNotRetryable() throws Exception {
        ClientCall call = mock(ClientCall.class);
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
    void testAfterSingletonsInstantiatedCallsInit() throws Exception {
        uut = spy(uut);
        when(blockingStub.handshake(any()))
                .thenReturn(conv.toProto(ServerConfig.of(ProtocolVersion.of(1, 999, 0),
                        new HashMap<>())));

        uut.afterSingletonsInstantiated();
        verify(uut).initialize();
    }

    @Test
    void testSubscribeNullParameters() throws Exception {
        expectNPE(() -> uut.subscribe(null, mock(FactObserver.class)));
        expectNPE(() -> uut.subscribe(mock(SubscriptionRequestTO.class), null));
        expectNPE(() -> uut.subscribe(null, null));
    }

    @Test
    void testSerialOfNullParameters() throws Exception {
        expectNPE(() -> uut.serialOf(null));
    }
//
//    @Test
//    public void testConfigureCompressionGZIPDisabledWhenServerReturnsNullCapability()
//            throws Exception {
//        uut.serverProperties(Maps.newHashMap(Capabilities.CODECS.toString(), null));
//        assertFalse(uut.configureCompression(Capabilities.CODEC_GZIP));
//    }
//
//    @Test
//    public void testConfigureCompressionGZIPDisabledWhenServerReturnsFalseCapability()
//            throws Exception {
//        uut.serverProperties(Maps.newHashMap(Capabilities.CODEC_GZIP.toString(), "false"));
//        assertFalse(uut.configureCompression(Capabilities.CODEC_GZIP));
//    }
//
//    @Test
//    public void testConfigureCompressionGZIPEnabledWhenServerReturnsCapability() throws Exception {
//        uut.serverProperties(Maps.newHashMap(Capabilities.CODEC_GZIP.toString(), "true"));
//        assertTrue(uut.configureCompression(Capabilities.CODEC_GZIP));
//    }
//
//    @Test
//    public void testConfigureCompressionGZIP() throws Exception {
//        uut = spy(uut);
//        uut.serverProperties(new HashMap<>());
//        uut.configureCompression();
//        verify(uut).configureCompression(Capabilities.CODEC_GZIP);
//    }
//
//    @Test
//    public void testConfigureCompressionLZ4() throws Exception {
//        uut = spy(uut);
//        uut.serverProperties(new HashMap<>());
//        when(uut.configureCompression(Capabilities.CODEC_LZ4)).thenReturn(true);
//        uut.configureCompression();
//        verify(uut, never()).configureCompression(Capabilities.CODEC_GZIP);
//    }

    @Test
    void testInvalidate() throws Exception {
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
    void testStateFor() throws Exception {
        assertThrows(NullPointerException.class, () -> uut.stateFor(Lists.emptyList(), null));
        assertThrows(NullPointerException.class, () -> uut.stateFor(null, null));
        assertThrows(NullPointerException.class, () -> uut.stateFor(null, Optional.of("foo")));

        {
            UUID id = new UUID(0, 1);
            StateForRequest req = new StateForRequest(Lists.emptyList(), "foo");
            when(blockingStub.stateFor(any())).thenReturn(conv.toProto(id));

            StateToken stateFor = uut.stateFor(Lists.emptyList(), Optional.of("foo"));
            verify(blockingStub).stateFor(conv.toProto(req));
        }

        {
            StateForRequest req = new StateForRequest(Lists.emptyList(), "foo");
            when(blockingStub.stateFor(any())).thenThrow(
                    new StatusRuntimeException(
                            Status.UNAVAILABLE));
            try {
                uut.stateFor(Lists.emptyList(), Optional.of("foo"));
                fail();
            } catch (RetryableException expected) {
            }
        }
    }

    @Test
    void testPublishIfUnchanged() throws Exception {
        assertThrows(NullPointerException.class, () -> uut.publishIfUnchanged(Lists.emptyList(), null));
        assertThrows(NullPointerException.class, () -> uut.publishIfUnchanged(null, null));
        assertThrows(NullPointerException.class, () -> uut.publishIfUnchanged(null, Optional.empty()));

        {
            UUID id = new UUID(0, 1);
            ConditionalPublishRequest req = new ConditionalPublishRequest(Lists.emptyList(), id);
            when(blockingStub.publishConditional(any())).thenReturn(conv.toProto(true));

            boolean publishIfUnchanged = uut.publishIfUnchanged(Lists.emptyList(), Optional.of(
                    new StateToken(id)));
            assertThat(publishIfUnchanged).isTrue();

            verify(blockingStub).publishConditional(conv.toProto(req));
        }

        {
            UUID id = new UUID(0, 1);
            ConditionalPublishRequest req = new ConditionalPublishRequest(Lists.emptyList(), id);
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

    }

    @Test
    void testSubscribe() throws Exception {
        assertThrows(NullPointerException.class, () -> uut.subscribe(mock(SubscriptionRequestTO.class), null));
        assertThrows(NullPointerException.class, () -> uut.subscribe(null, null));
        assertThrows(NullPointerException.class, () -> uut.subscribe(null, mock(FactObserver.class)));

    }

    @Test
    void testCredentialsWrongFormat() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> new GrpcFactStore(mock(Channel.class), Optional.ofNullable("xyz")));

        assertThrows(IllegalArgumentException.class, () -> new GrpcFactStore(mock(Channel.class), Optional.ofNullable("x:y:z")));

        assertThat(new GrpcFactStore(mock(Channel.class), Optional.ofNullable("xyz:abc")))
                .isNotNull();

    }

    @Test
    void testCredentialsRightFormat() throws Exception {
        assertThat(new GrpcFactStore(mock(Channel.class), Optional.ofNullable("xyz:abc")))
                .isNotNull();
    }
}
