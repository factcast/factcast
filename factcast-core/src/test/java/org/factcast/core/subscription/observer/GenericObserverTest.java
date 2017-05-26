package org.factcast.core.subscription.observer;

import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import org.factcast.core.TestFact;
import org.junit.Before;
import org.junit.Test;

public class GenericObserverTest {

    @Before
    public void setUp() throws Exception {
    }

    @Test
    public void testMap() throws Exception {
        GenericObserver<Integer> i = spy(new GenericObserver<Integer>() {

            @Override
            public void onNext(Integer element) {

            }
        });

        FactObserver mapped = i.map(f -> 4);

        verify(i, never()).onCatchup();
        mapped.onCatchup();
        verify(i).onCatchup();

        verify(i, never()).onError(any());
        mapped.onError(new Throwable("ignore me") {
            @Override
            public StackTraceElement[] getStackTrace() {
                return new StackTraceElement[0];
            }
        });
        verify(i).onError(any());

        verify(i, never()).onComplete();
        mapped.onComplete();
        verify(i).onComplete();

        verify(i, never()).onNext(any());
        mapped.onNext(new TestFact());
        verify(i).onNext(4);

    }

    @Test(expected = NullPointerException.class)
    public void testMapNull() throws Exception {
        GenericObserver<Integer> i = new GenericObserver<Integer>() {

            @Override
            public void onNext(Integer element) {

            }
        };

        i.map(null);
    }

}
