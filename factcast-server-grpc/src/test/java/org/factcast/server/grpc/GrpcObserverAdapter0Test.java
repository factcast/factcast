package org.factcast.server.grpc;

import static org.factcast.core.TestHelper.*;
import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import java.util.function.Function;

import org.factcast.core.Fact;
import org.factcast.core.Test0Fact;
import org.factcast.grpc.api.conv.ProtoConverter;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_Notification;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import io.grpc.stub.StreamObserver;

@SuppressWarnings({ "rawtypes", "unchecked" })
@RunWith(MockitoJUnitRunner.class)
public class GrpcObserverAdapter0Test {

    @Mock
    private StreamObserver<MSG_Notification> observer;

    @Mock
    private Function<Fact, MSG_Notification> projection;

    @Captor
    private ArgumentCaptor<MSG_Notification> msg;

    @Test
    public void testNullsOnConstructor() throws Exception {

        String id = "id";
        StreamObserver so = mock(StreamObserver.class);
        Function p = mock(Function.class);

        expectNPE(() -> new GrpcObserverAdapter(null, so, p));
        expectNPE(() -> new GrpcObserverAdapter(null, null, p));
        expectNPE(() -> new GrpcObserverAdapter(null, null, null));

        expectNPE(() -> new GrpcObserverAdapter(id, so, null));
        expectNPE(() -> new GrpcObserverAdapter(id, null, p));
        expectNPE(() -> new GrpcObserverAdapter(id, null, null));

    }

    @Test
    public void testOnComplete() throws Exception {
        GrpcObserverAdapter uut = new GrpcObserverAdapter("foo", observer, projection);

        uut.onComplete();

        verify(observer).onCompleted();
    }

    @Test
    public void testOnCatchup() throws Exception {
        GrpcObserverAdapter uut = new GrpcObserverAdapter("foo", observer, projection);

        doNothing().when(observer).onNext(msg.capture());
        verify(observer, never()).onNext(any());
        uut.onCatchup();

        verify(observer).onNext(any());
        assertEquals(MSG_Notification.Type.Catchup, msg.getValue().getType());

    }

    @Test
    public void testOnError() throws Exception {
        GrpcObserverAdapter uut = new GrpcObserverAdapter("foo", observer, projection);

        verify(observer, never()).onNext(any());
        uut.onError(new Exception());

        verify(observer).onError(any());
    }

    @Test
    public void testOnNext() throws Exception {
        ProtoConverter conv = new ProtoConverter();
        GrpcObserverAdapter uut = new GrpcObserverAdapter("foo", observer,
                conv::createNotificationFor);
        doNothing().when(observer).onNext(msg.capture());
        verify(observer, never()).onNext(any());

        final Test0Fact f = new Test0Fact();
        uut.onNext(f);

        verify(observer).onNext(any());
        assertEquals(MSG_Notification.Type.Fact, msg.getValue().getType());
        assertEquals(f.id(), conv.fromProto(msg.getValue().getFact()).id());

    }
}
