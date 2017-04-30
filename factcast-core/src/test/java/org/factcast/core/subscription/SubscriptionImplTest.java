package org.factcast.core.subscription;

import static org.junit.Assert.*;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeoutException;

import org.factcast.core.Fact;
import org.factcast.core.subscription.observer.GenericObserver;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class SubscriptionImplTest {
    @Mock
    private GenericObserver<Fact> observer;

    @InjectMocks
    private SubscriptionImpl<Fact> uut;

    @Before
    public void setUp() throws Exception {
    }

    @Test
    public void testClose() throws Exception {

        expect(TimeoutException.class, () -> uut.awaitCatchup(10));
        expect(TimeoutException.class, () -> uut.awaitComplete(10));

        uut.close();

        expect(SubscriptionCancelledException.class, () -> uut.awaitCatchup(10));
        expect(SubscriptionCancelledException.class, () -> uut.awaitComplete(10));

    }

    private void expect(Class<? extends Throwable> ex, Callable<?> e) {
        try {
            e.call();
            fail("expected " + ex);
        } catch (Throwable actual) {
            if (!ex.isInstance(actual)) {
                fail("Wrong exception, expected " + ex + " but got " + actual);
            }

        }
    }

    @SuppressWarnings("resource")
    @Test(expected = NullPointerException.class)
    public void testNullConst() throws Exception {
        new SubscriptionImpl<>(null);
    }

}
