package org.factcast.core.subscription.observer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.factcast.core.Fact;
import org.factcast.core.subscription.observer.GenericObserver.ObserverBridge;
import org.junit.jupiter.api.Test;

public class ObserverBridgeTest {

    ObserverBridge<?> uut = new ObserverBridge<Fact>(mock(FactObserver.class), f -> f);

    @Test
    public void testNullParameterContracts() throws Exception {
        assertThrows(NullPointerException.class, () -> {
            uut.onNext(null);
        });
        assertThrows(NullPointerException.class, () -> {
            uut.onError(null);
        });

        uut.onNext(Fact.builder().build("{}"));
        uut.onError(new RuntimeException());
    }

}
