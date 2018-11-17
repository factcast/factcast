package org.factcast.core.subscription.observer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import org.factcast.core.Test0Fact;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

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

    @Test
    public void testMapNull() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            GenericObserver<Integer> i = element -> {
            };
            i.map(null);
        });
    }
}
