package org.factcast.client.grpc;

import static org.factcast.core.TestHelper.expectNPE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;

import org.factcast.core.Fact;
import org.factcast.core.Test0Fact;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.core.subscription.observer.FactObserver;
import org.factcast.grpc.api.conv.ProtoConverter;
import org.factcast.grpc.api.conv.ProtocolVersion;
import org.factcast.grpc.api.conv.ServerConfig;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_Empty;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_Facts;
import org.factcast.grpc.api.gen.RemoteFactStoreGrpc.RemoteFactStoreBlockingStub;
import org.factcast.grpc.api.gen.RemoteFactStoreGrpc.RemoteFactStoreStub;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.grpc.Channel;
import net.devh.springboot.autoconfigure.grpc.client.AddressChannelFactory;

@ExtendWith(MockitoExtension.class)
public class GrpcFactStore0Test {

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
    public void testFetchByIdNotFound() {
        UUID id = UUID.randomUUID();
        when(blockingStub.fetchById(eq(conv.toProto(id)))).thenReturn(conv.toProto(Optional
                .empty()));
        Optional<Fact> fetchById = uut.fetchById(id);
        assertFalse(fetchById.isPresent());
    }

    @Test
    public void testFetchByIdFound() {
        UUID id = UUID.randomUUID();
        when(blockingStub.fetchById(eq(conv.toProto(id)))).thenReturn(conv.toProto(Optional.of(Fact
                .builder()
                .ns("test")
                .build("{}"))));
        Optional<Fact> fetchById = uut.fetchById(id);
        assertTrue(fetchById.isPresent());
    }

    @Test
    public void testPublish() {
        when(blockingStub.publish(factsCap.capture())).thenReturn(MSG_Empty.newBuilder().build());
        final Test0Fact fact = new Test0Fact();
        uut.publish(Collections.singletonList(fact));
        verify(blockingStub).publish(any());
        final MSG_Facts pfacts = factsCap.getValue();
        Fact published = conv.fromProto(pfacts.getFact(0));
        assertEquals(fact.id(), published.id());
    }

    static class SomeException extends RuntimeException {

        private static final long serialVersionUID = 1L;
    }

    @Test
    public void testPublishPropagatesException() {
        Assertions.assertThrows(SomeException.class, () -> {
            when(blockingStub.publish(any())).thenThrow(new SomeException());
            uut.publish(Collections.singletonList(Fact.builder().build("{}")));
        });
    }

    @Test
    public void testConstruction() {
        expectNPE(() -> new GrpcFactStore((AddressChannelFactory) null));
        expectNPE(() -> new GrpcFactStore((Channel) null));
        expectNPE(() -> new GrpcFactStore(mock(RemoteFactStoreBlockingStub.class), null));
        expectNPE(() -> new GrpcFactStore(null, mock(RemoteFactStoreStub.class)));
        expectNPE(() -> new GrpcFactStore(null, null));
    }

    @Test
    public void testSubscribeNull() {
        expectNPE(() -> uut.subscribe(null, mock(FactObserver.class)));
        expectNPE(() -> uut.subscribe(null, null));
        expectNPE(() -> uut.subscribe(mock(SubscriptionRequestTO.class), null));
    }

    @Test
    public void testMatchingProtocolVersion() {
        when(blockingStub.handshake(any())).thenReturn(conv.toProto(ServerConfig.of(ProtocolVersion
                .of(1, 0, 0), new HashMap<>())));
        uut.initialize();
    }

    @Test
    public void testCompatibleProtocolVersion() {
        when(blockingStub.handshake(any())).thenReturn(conv.toProto(ServerConfig.of(ProtocolVersion
                .of(1, 1, 0), new HashMap<>())));
        uut.initialize();
    }

    @Test
    public void testIncompatibleProtocolVersion() {
        Assertions.assertThrows(IncompatibleProtocolVersions.class, () -> {
            when(blockingStub.handshake(any())).thenReturn(conv.toProto(ServerConfig.of(
                    ProtocolVersion.of(2, 0, 0), new HashMap<>())));
            uut.initialize();
        });
    }
}
