package org.factcast.server.grpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.concurrent.Callable;
import java.util.function.Function;

import org.factcast.core.Fact;
import org.factcast.grpc.api.conv.ProtoConverter;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_Notification;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.grpc.stub.StreamObserver;

@SuppressWarnings({ "rawtypes", "unchecked" })
@ExtendWith(MockitoExtension.class)
public class GrpcObserverAdapter0Test {

    @Mock
    private StreamObserver<MSG_Notification> observer;

    @Mock
    private Function<Fact, MSG_Notification> projection;

    @Captor
    private ArgumentCaptor<MSG_Notification> msg;

    @Test
    public void testNullsOnConstructor() {
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
    public void testOnComplete() {
        GrpcObserverAdapter uut = new GrpcObserverAdapter("foo", observer, projection);
        uut.onComplete();
        verify(observer).onCompleted();
    }

    @Test
    public void testOnCatchup() {
        GrpcObserverAdapter uut = new GrpcObserverAdapter("foo", observer, projection);
        doNothing().when(observer).onNext(msg.capture());
        verify(observer, never()).onNext(any());
        uut.onCatchup();
        verify(observer).onNext(any());
        assertEquals(MSG_Notification.Type.Catchup, msg.getValue().getType());
    }

    @Test
    public void testOnError() {
        GrpcObserverAdapter uut = new GrpcObserverAdapter("foo", observer, projection);
        verify(observer, never()).onNext(any());
        uut.onError(new Exception());
        verify(observer).onError(any());
    }

    @Test
    public void testOnNext() {
        ProtoConverter conv = new ProtoConverter();
        GrpcObserverAdapter uut = new GrpcObserverAdapter("foo", observer,
                conv::createNotificationFor);
        doNothing().when(observer).onNext(msg.capture());
        verify(observer, never()).onNext(any());
        Fact f = Fact.builder().ns("test").build("{}");
        uut.onNext(f);
        verify(observer).onNext(any());
        assertEquals(MSG_Notification.Type.Fact, msg.getValue().getType());
        assertEquals(f.id(), conv.fromProto(msg.getValue().getFact()).id());
    }

    public static void expectNPE(Callable<?> e) {
        expect(NullPointerException.class, e);
    }

    public static void expect(Class<? extends Throwable> ex, Callable<?> e) {
        try {
            e.call();
            fail("expected " + ex);
        } catch (Throwable actual) {
            if (!ex.isInstance(actual)) {
                fail("Wrong exception, expected " + ex + " but got " + actual);
            }
        }
    }
}
