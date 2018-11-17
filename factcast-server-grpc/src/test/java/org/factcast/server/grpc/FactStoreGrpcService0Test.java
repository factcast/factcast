package org.factcast.server.grpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.factcast.core.Fact;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.store.FactStore;
import org.factcast.core.subscription.SubscriptionRequest;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.grpc.api.conv.ProtoConverter;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_Fact;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_Facts;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_Facts.Builder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;

@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
public class FactStoreGrpcService0Test {

    @Mock
    FactStore backend;

    FactStoreGrpcService uut;

    @Captor
    ArgumentCaptor<List<Fact>> acFactList;

    final ProtoConverter protoConverter = new ProtoConverter();

    @Captor
    private ArgumentCaptor<SubscriptionRequestTO> reqCaptor;

    @BeforeEach
    public void setUp() {
        uut = new FactStoreGrpcService(backend);
    }

    @Test
    public void testPublishNull() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            uut.publish(null, mock(StreamObserver.class));
        });
    }

    @Test
    public void testPublishNone() {
        doNothing().when(backend).publish(acFactList.capture());
        MSG_Facts r = MSG_Facts.newBuilder().build();
        uut.publish(r, mock(StreamObserver.class));
        verify(backend).publish(anyList());
        assertTrue(acFactList.getValue().isEmpty());
    }

    @Test
    public void testPublishSome() {
        doNothing().when(backend).publish(acFactList.capture());
        Builder b = MSG_Facts.newBuilder();
        Fact f1 = Fact.builder().ns("test").build("{}");
        Fact f2 = Fact.builder().ns("test").build("{}");
        MSG_Fact msg1 = protoConverter.toProto(f1);
        MSG_Fact msg2 = protoConverter.toProto(f2);
        b.addAllFact(Arrays.asList(msg1, msg2));
        MSG_Facts r = b.build();
        uut.publish(r, mock(StreamObserver.class));
        verify(backend).publish(anyList());
        List<Fact> facts = acFactList.getValue();
        assertFalse(facts.isEmpty());
        assertEquals(2, facts.size());
        assertEquals(f1.id(), facts.get(0).id());
        assertEquals(f2.id(), facts.get(1).id());
    }

    @Test
    public void testFetchByIdNull() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            uut.fetchById(null, mock(StreamObserver.class));
        });
    }

    @Test
    public void testFetchById() {
        UUID id = UUID.randomUUID();
        uut.fetchById(protoConverter.toProto(id), mock(ServerCallStreamObserver.class));
        verify(backend).fetchById(eq(id));
    }

    @Test
    public void testSubscribeFacts() {
        SubscriptionRequest req = SubscriptionRequest.catchup(FactSpec.forMark()).fromNowOn();
        when(backend.subscribe(this.reqCaptor.capture(), any())).thenReturn(null);
        uut.subscribe(new ProtoConverter().toProto(SubscriptionRequestTO.forFacts(req)), mock(
                ServerCallStreamObserver.class));
        verify(backend).subscribe(any(), any());
        assertFalse(reqCaptor.getValue().idOnly());
    }

    @Test
    public void testSubscribeIds() {
        SubscriptionRequest req = SubscriptionRequest.catchup(FactSpec.forMark()).fromNowOn();
        when(backend.subscribe(this.reqCaptor.capture(), any())).thenReturn(null);
        uut.subscribe(new ProtoConverter().toProto(SubscriptionRequestTO.forIds(req)), mock(
                ServerCallStreamObserver.class));
        verify(backend).subscribe(any(), any());
        assertTrue(reqCaptor.getValue().idOnly());
    }
}
