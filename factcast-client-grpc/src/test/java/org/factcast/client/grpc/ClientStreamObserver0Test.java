package org.factcast.client.grpc;

import org.factcast.core.Fact;
import org.factcast.core.IdOnlyFact;
import org.factcast.core.subscription.SubscriptionImpl;
import org.factcast.core.subscription.observer.FactObserver;
import org.factcast.grpc.api.conv.ProtoConverter;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_Notification;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class ClientStreamObserver0Test {

    @Mock
    FactObserver factObserver;

    ClientStreamObserver uut;

    ProtoConverter converter = new ProtoConverter();

    private SubscriptionImpl<Fact> subscription;

    @Before
    public void setUp() {
        subscription = spy(new SubscriptionImpl<>(factObserver));
        uut = new ClientStreamObserver(subscription);
    }

    @Test(expected = NullPointerException.class)
    public void testConstructorNull() {
        new ClientStreamObserver(null);
    }

    @Test
    public void testOnNext() {
        Fact f = Fact.of("{\"ns\":\"ns\",\"id\":\"" + UUID.randomUUID() + "\"}", "{}");
        MSG_Notification n = converter.createNotificationFor(f);
        uut.onNext(n);
        verify(factObserver).onNext(eq(f));
    }

    @Test
    public void testOnNextId() {
        MSG_Notification n = converter.createNotificationFor(UUID.randomUUID());
        uut.onNext(n);
        verify(factObserver).onNext(any(IdOnlyFact.class));
    }

    @Test
    public void testOnCatchup() {
        uut.onNext(converter.createCatchupNotification());
        verify(factObserver).onCatchup();
    }

    @Test
    public void testOnComplete() {
        uut.onNext(converter.createCompleteNotification());
        verify(factObserver).onComplete();
    }

    @Test
    public void testOnTransportComplete() {
        uut.onCompleted();
        verify(factObserver).onComplete();
    }

    @Test
    public void testOnError() {
        uut.onError(new IOException());
        verify(factObserver).onError(any());
    }

}
