package org.factcast.core.subscription.observer;

import org.factcast.core.Test0Fact;
import org.junit.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class GenericObserver0Test {

    @Test
    public void testMap() {
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

            private static final long serialVersionUID = 1L;

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
        mapped.onNext(new Test0Fact());
        verify(i).onNext(4);

    }

    @Test(expected = NullPointerException.class)
    public void testMapNull() {
        GenericObserver<Integer> i = element -> {

        };

        i.map(null);
    }

}
