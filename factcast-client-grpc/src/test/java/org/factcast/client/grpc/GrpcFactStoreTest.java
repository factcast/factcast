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

import static org.factcast.core.TestHelper.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;

import org.factcast.core.Fact;
import org.factcast.core.TestFact;
import org.factcast.core.store.RetryableException;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.core.subscription.observer.FactObserver;
import org.factcast.grpc.api.conv.ProtoConverter;
import org.factcast.grpc.api.conv.ProtocolVersion;
import org.factcast.grpc.api.conv.ServerConfig;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_Empty;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_Facts;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_Notification;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_SubscriptionRequest;
import org.factcast.grpc.api.gen.RemoteFactStoreGrpc.RemoteFactStoreBlockingStub;
import org.factcast.grpc.api.gen.RemoteFactStoreGrpc.RemoteFactStoreStub;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;

import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import net.devh.springboot.autoconfigure.grpc.client.AddressChannelFactory;

@ExtendWith(MockitoExtension.class)
public class GrpcFactStoreTest {

    @InjectMocks
    private GrpcFactStore uut;

    @Mock
    private RemoteFactStoreBlockingStub blockingStub;

    @Mock
    private RemoteFactStoreStub stub;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private SubscriptionRequestTO req;

    private ProtoConverter conv = new ProtoConverter();

    @Captor
    private ArgumentCaptor<MSG_Facts> factsCap;

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
        assertThrows(NullPointerException.class, () -> {
            uut.publish(null);
        });
    }

    static class SomeException extends RuntimeException {

        private static final long serialVersionUID = 1L;
    }

    @Test
    void testPublishPropagatesException() {
        when(blockingStub.publish(any())).thenThrow(new SomeException());
        assertThrows(SomeException.class, () -> {
            uut.publish(Collections.singletonList(Fact.builder().build("{}")));
        });
    }

    @Test
    void testFetchByIdPropagatesRetryableExceptionOnUnavailableStatus() {
        when(blockingStub.fetchById(any())).thenThrow(new StatusRuntimeException(
                Status.UNAVAILABLE));
        assertThrows(RetryableException.class, () -> {
            uut.fetchById(UUID.randomUUID());
        });
    }

    @Test
    void testPublishPropagatesRetryableExceptionOnUnavailableStatus() {
        when(blockingStub.publish(any())).thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));
        assertThrows(RetryableException.class, () -> {
            uut.publish(Collections.singletonList(Fact.builder().build("{}")));
        });
    }

    @SuppressWarnings("unchecked")
    @Test
    void testCancelNotRetryableExceptionOnUnavailableStatus() {
        ClientCall<MSG_SubscriptionRequest, MSG_Notification> call = mock(ClientCall.class);
        doThrow(new StatusRuntimeException(Status.UNAVAILABLE)).when(call).cancel(any(), any());
        assertThrows(StatusRuntimeException.class, () -> {
            uut.cancel(call);
        });
    }

    @Test
    void testSerialOfPropagatesRetryableExceptionOnUnavailableStatus() {
        when(blockingStub.serialOf(any())).thenThrow(new StatusRuntimeException(
                Status.UNAVAILABLE));
        assertThrows(RetryableException.class, () -> {
            uut.serialOf(mock(UUID.class));
        });
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
        assertThrows(RetryableException.class, () -> {
            uut.initialize();
        });
    }

    @Test
    void testEnumerateNamespacesPropagatesRetryableExceptionOnUnavailableStatus() {
        when(blockingStub.enumerateNamespaces(any())).thenThrow(new StatusRuntimeException(
                Status.UNAVAILABLE));
        assertThrows(RetryableException.class, () -> {
            uut.enumerateNamespaces();
        });
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
        assertThrows(RetryableException.class, () -> {
            uut.enumerateTypes("ns");
        });
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
    void testConstruction() {
        expectNPE(() -> new GrpcFactStore((AddressChannelFactory) null));
        expectNPE(() -> new GrpcFactStore((Channel) null));
        expectNPE(() -> new GrpcFactStore(mock(RemoteFactStoreBlockingStub.class), null));
        expectNPE(() -> new GrpcFactStore(null, mock(RemoteFactStoreStub.class)));
        expectNPE(() -> new GrpcFactStore(null, null));
    }

    @Test
    void testSubscribeNull() {
        expectNPE(() -> uut.subscribe(null, mock(FactObserver.class)));
        expectNPE(() -> uut.subscribe(null, null));
        expectNPE(() -> uut.subscribe(mock(SubscriptionRequestTO.class), null));
    }

    @Test
    void testMatchingProtocolVersion() {
        when(blockingStub.handshake(any()))
                .thenReturn(conv.toProto(ServerConfig.of(ProtocolVersion.of(1, 0, 0),
                        new HashMap<>())));
        uut.initialize();
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
                .thenReturn(conv.toProto(ServerConfig.of(ProtocolVersion.of(2, 0, 0),
                        new HashMap<>())));
        Assertions.assertThrows(IncompatibleProtocolVersions.class, () -> {
            uut.initialize();
        });
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
    public void testWrapRetryable_nonRetryable() throws Exception {
        StatusRuntimeException cause = new StatusRuntimeException(Status.DEADLINE_EXCEEDED);
        RuntimeException e = GrpcFactStore.wrapRetryable(cause);
        assertTrue(e instanceof StatusRuntimeException);
        assertSame(e, cause);
    }

    @Test
    public void testWrapRetryable() throws Exception {
        StatusRuntimeException cause = new StatusRuntimeException(Status.UNAVAILABLE);
        RuntimeException e = GrpcFactStore.wrapRetryable(cause);
        assertTrue(e instanceof RetryableException);
        assertSame(e.getCause(), cause);
    }

    @Test
    public void testCancelIsPropagated() throws Exception {
        ClientCall call = mock(ClientCall.class);
        uut.cancel(call);
        verify(call).cancel(any(), any());
    }

    @Test
    public void testCancelIsNotRetryable() throws Exception {
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
    public void testAfterSingletonsInstantiatedCallsInit() throws Exception {
        uut = spy(uut);
        when(blockingStub.handshake(any()))
                .thenReturn(conv.toProto(ServerConfig.of(ProtocolVersion.of(1, 999, 0),
                        new HashMap<>())));

        uut.afterSingletonsInstantiated();
        verify(uut).initialize();
    }

    @Test
    public void testSubscribeNullParameters() throws Exception {
        expectNPE(() -> {
            uut.subscribe(null, mock(FactObserver.class));
        });
        expectNPE(() -> {
            uut.subscribe(mock(SubscriptionRequestTO.class), null);
        });
        expectNPE(() -> {
            uut.subscribe(null, null);
        });
    }

    @Test
    public void testSerialOfNullParameters() throws Exception {
        expectNPE(() -> {
            uut.serialOf(null);
        });
    }

    @Test
    public void testConfigureCompressionGZIP() throws Exception {
        uut = spy(uut);
        uut.configureCompression();
        verify(uut).configureGZip();
    }

    @Test
    public void testConfigureCompressionLZ4() throws Exception {
        uut = spy(uut);
        when(uut.configureLZ4()).thenReturn(true);
        uut.configureCompression();
        verify(uut, never()).configureGZip();
        verify(uut).configureLZ4();

    }
}
