package org.factcast.core.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

import org.factcast.core.store.FactStore;
import org.factcast.core.subscription.observer.FactObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ReconnectingFactSubscriptionWrapperTest {
    @Mock
    private FactStore store;

    @Mock
    private SubscriptionRequestTO req;

    @Mock
    private FactObserver obs;

    ReconnectingFactSubscriptionWrapper uut;

    private ArgumentCaptor<FactObserver> observerAC = ArgumentCaptor.forClass(FactObserver.class);

    @Mock
    private Subscription subscription;

    @BeforeEach
    public void setup() {
        when(store.subscribe(any(), observerAC.capture())).thenReturn(subscription);
        uut = new ReconnectingFactSubscriptionWrapper(store, req, obs);
    }

    @Test
    public void testAwaitComplete() throws Exception {

        observerAC.getValue().onComplete();

        assertTimeout(Duration.ofMillis(1000), () -> {
            // needs to return immediately
            uut.awaitComplete();
        });
    }

    @Test
    public void testAwaitCompleteLong() throws Exception {
        when(subscription.awaitComplete(anyLong())).thenThrow(TimeoutException.class)
                .then(x -> subscription);

        assertThrows(TimeoutException.class, () -> {
            uut.awaitComplete(51);
        });

        assertTimeout(Duration.ofMillis(100), () -> {
            assertThat(uut.awaitComplete(52)).isSameAs(uut);
        });
        // await call was passed
        verify(subscription).awaitComplete(52);

    }

    @Test
    public void testAssertSubscriptionStateNotClosed() throws Exception {
        uut.close();
        assertThrows(SubscriptionCancelledException.class, () -> {
            uut.awaitCatchup();
        });
        assertThrows(SubscriptionCancelledException.class, () -> {
            uut.awaitCatchup(1L);
        });
        assertThrows(SubscriptionCancelledException.class, () -> {
            uut.awaitComplete();
        });
        assertThrows(SubscriptionCancelledException.class, () -> {
            uut.awaitComplete(1L);
        });
    }

}
