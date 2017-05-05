package org.factcast.core.subscription;

import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import org.factcast.core.subscription.observer.GenericObserver;
import org.junit.Before;
import org.junit.Test;

public class SubscriptionsTest {

    private GenericObserver<Integer> obs;

    @Before
    public void setUp() throws Exception {
        obs = mock(GenericObserver.class);
    }

    @Test(expected = NullPointerException.class)
    public void testOnNull() throws Exception {
        Subscriptions.on(null);
    }

    @Test
    public void testOn() throws Exception {
        SubscriptionImpl<Integer> on = Subscriptions.on(obs);

        verify(obs, never()).onNext(any());
        on.notifyElement(1);
        verify(obs).onNext(1);

        verify(obs, never()).onCatchup();
        on.notifyCatchup();
        verify(obs).onCatchup();

        verify(obs, never()).onComplete();
        on.notifyComplete();
        verify(obs).onComplete();

        verify(obs, never()).onError(any());
        on.notifyError(new Throwable("ignore me"));
        verify(obs).onError(any());

    }
}
