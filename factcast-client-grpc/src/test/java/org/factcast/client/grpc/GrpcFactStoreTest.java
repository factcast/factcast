package org.factcast.client.grpc;

import static org.factcast.core.TestHelper.*;
import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import org.factcast.core.Fact;
import org.factcast.core.TestFact;
import org.factcast.grpc.api.conv.ProtoConverter;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_Empty;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_Facts;
import org.factcast.grpc.api.gen.RemoteFactStoreGrpc.RemoteFactStoreBlockingStub;
import org.factcast.grpc.api.gen.RemoteFactStoreGrpc.RemoteFactStoreStub;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import io.grpc.Channel;
import net.devh.springboot.autoconfigure.grpc.client.AddressChannelFactory;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ RemoteFactStoreBlockingStub.class, RemoteFactStoreStub.class })
public class GrpcFactStoreTest {

    @InjectMocks
    private GrpcFactStore uut;

    @Mock
    private RemoteFactStoreBlockingStub blockingStub;

    @Mock
    private RemoteFactStoreStub stub;

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
                new TestFact())));

        Optional<Fact> fetchById = uut.fetchById(id);

        assertTrue(fetchById.isPresent());
    }

    @Test
    public void testPublish() throws Exception {

        when(blockingStub.publish(factsCap.capture())).thenReturn(MSG_Empty.newBuilder().build());

        final TestFact fact = new TestFact();
        uut.publish(Arrays.asList(fact));
        verify(blockingStub).publish(any());

        final MSG_Facts pfacts = factsCap.getValue();
        Fact published = conv.fromProto(pfacts.getFact(0));
        assertEquals(fact.id(), published.id());
    }

    @Test
    public void testConstruction() throws Exception {
        expectNPE(() -> new GrpcFactStore((AddressChannelFactory) null));
        expectNPE(() -> new GrpcFactStore((Channel) null));
        expectNPE(() -> new GrpcFactStore(mock(RemoteFactStoreBlockingStub.class), null));
        expectNPE(() -> new GrpcFactStore(null, mock(RemoteFactStoreStub.class)));
    }
}
