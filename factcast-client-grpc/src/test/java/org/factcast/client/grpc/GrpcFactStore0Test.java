package org.factcast.client.grpc;

import static org.factcast.core.TestHelper.expectNPE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
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
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import io.grpc.Channel;
import net.devh.springboot.autoconfigure.grpc.client.AddressChannelFactory;

@RunWith(org.mockito.junit.MockitoJUnitRunner.class)
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

    @Before
    public void setUp() throws Exception {

    }

    @Test
    public void testFetchByIdNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(blockingStub.fetchById(eq(conv.toProto(id)))).thenReturn(conv.toProto(Optional
                .empty()));

        Optional<Fact> fetchById = uut.fetchById(id);

        assertFalse(fetchById.isPresent());
    }

    @Test
    public void testFetchByIdFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(blockingStub.fetchById(eq(conv.toProto(id)))).thenReturn(conv.toProto(Optional.of(
                new Test0Fact())));

        Optional<Fact> fetchById = uut.fetchById(id);

        assertTrue(fetchById.isPresent());
    }

    @Test
    public void testPublish() throws Exception {

        when(blockingStub.publish(factsCap.capture())).thenReturn(MSG_Empty.newBuilder().build());

        final Test0Fact fact = new Test0Fact();
        uut.publish(Arrays.asList(fact));
        verify(blockingStub).publish(any());

        final MSG_Facts pfacts = factsCap.getValue();
        Fact published = conv.fromProto(pfacts.getFact(0));
        assertEquals(fact.id(), published.id());
    }

    static class SomeException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    };

    @Test(expected = SomeException.class)
    public void testPublishPropagatesException() throws Exception {
        when(blockingStub.publish(any())).thenThrow(new SomeException());
        uut.publish(Arrays.asList(Fact.builder().build("{}")));
    }

    @Test
    public void testConstruction() throws Exception {
        expectNPE(() -> new GrpcFactStore((AddressChannelFactory) null));
        expectNPE(() -> new GrpcFactStore((Channel) null));
        expectNPE(() -> new GrpcFactStore(mock(RemoteFactStoreBlockingStub.class), null));
        expectNPE(() -> new GrpcFactStore(null, mock(RemoteFactStoreStub.class)));
        expectNPE(() -> new GrpcFactStore(null, null));
    }

    @Test
    public void testSubscribeNull() throws Exception {
        expectNPE(() -> uut.subscribe(null, mock(FactObserver.class)));
        expectNPE(() -> uut.subscribe(null, null));
        expectNPE(() -> uut.subscribe(mock(SubscriptionRequestTO.class), null));
    }

    @Test
    public void testMatchingProtocolVersion() throws Exception {
        when(blockingStub.handshake(any())).thenReturn(
                conv.toProto(
                        ServerConfig.of(
                                ProtocolVersion.of(1, 0, 0),
                                new HashMap<>())));

        uut.initialize();
    }

    @Test
    public void testCompatibleProtocolVersion() throws Exception {
        when(blockingStub.handshake(any())).thenReturn(
                conv.toProto(
                        ServerConfig.of(
                                ProtocolVersion.of(1, 1, 0),
                                new HashMap<>())));
        uut.initialize();
    }

    @Test(expected = IncompatibleProtocolVersions.class)
    public void testIncompatibleProtocolVersion() throws Exception {
        when(blockingStub.handshake(any())).thenReturn(
                conv.toProto(
                        ServerConfig.of(
                                ProtocolVersion.of(2, 0, 0),
                                new HashMap<>())));

        uut.initialize();
    }

}
