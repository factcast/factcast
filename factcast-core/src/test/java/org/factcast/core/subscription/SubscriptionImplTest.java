package org.factcast.core.subscription;

import static org.factcast.core.TestHelper.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeoutException;

import org.factcast.core.Fact;
import org.factcast.core.subscription.observer.GenericObserver;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class SubscriptionImplTest {

    @Mock
    private GenericObserver<Fact> observer;

    @InjectMocks
    private SubscriptionImpl<Fact> uut;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        obs = mock(GenericObserver.class);
    }

    @Test
    void testClose() throws Exception {
        expect(TimeoutException.class, () -> uut.awaitCatchup(10));
        expect(TimeoutException.class, () -> uut.awaitComplete(10));
        uut.close();
        expect(SubscriptionCancelledException.class, () -> uut.awaitCatchup(10));
        expect(SubscriptionCancelledException.class, () -> uut.awaitComplete(10));
    }

    @Test
    void testAwaitCatchup() {
        expect(TimeoutException.class, () -> uut.awaitCatchup(10));
        expect(TimeoutException.class, () -> uut.awaitComplete(10));
        uut.notifyCatchup();
        uut.awaitCatchup();
        expect(TimeoutException.class, () -> uut.awaitComplete(10));
    }

    @Test
    void testAwaitComplete() {
        expect(TimeoutException.class, () -> uut.awaitCatchup(10));
        expect(TimeoutException.class, () -> uut.awaitComplete(10));
        uut.notifyComplete();
        uut.awaitCatchup();
        uut.awaitComplete();
    }

    @SuppressWarnings("resource")
    @Test
    void testNullConst() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            new SubscriptionImpl<>(null);
        });
    }

    @Test
    void testNotifyElementNull() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            uut.notifyElement(null);
        });
    }

    @Test
    void testNotifyErrorNull() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            uut.notifyError(null);
        });
    }

    @Test
    void testOnClose() throws Exception {
        CountDownLatch l = new CountDownLatch(1);
        uut.onClose(l::countDown);
        uut.close();
        l.await();
    }

    private GenericObserver<Integer> obs;

    @Test
    void testOnNull() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            SubscriptionImpl.on(null);
        });
    }

    @Test
    void testOn() {
        SubscriptionImpl<Integer> on = SubscriptionImpl.on(obs);
        verify(obs, never()).onNext(any());
        on.notifyElement(1);
        verify(obs).onNext(1);
        verify(obs, never()).onCatchup();
        on.notifyCatchup();
        verify(obs).onCatchup();
        verify(obs, never()).onComplete();
        on.notifyComplete();
        verify(obs).onComplete();
        // subsequent calls will be ignored, as this subscription was completed.
        // creating a new one...
    }

    @Test
    void testOnError() {
        SubscriptionImpl<Integer> on = SubscriptionImpl.on(obs);
        verify(obs, never()).onError(any());
        on.notifyError(new Throwable("ignore me"));
        verify(obs).onError(any());
    }

    @Test
    void testOnErrorCloses() {
        SubscriptionImpl<Integer> on = SubscriptionImpl.on(obs);
        on.notifyError(new Throwable("ignore me"));
        on.notifyElement(1);
        on.notifyCatchup();
        on.notifyComplete();
        verify(obs, never()).onComplete();
        verify(obs, never()).onCatchup();
        verify(obs, never()).onNext(anyInt());
    }

    @Test
    void testOnCompleteCloses() {
        SubscriptionImpl<Integer> on = SubscriptionImpl.on(obs);
        on.notifyComplete();
        on.notifyElement(1);
        on.notifyCatchup();
        on.notifyError(new Throwable("ignore me"));
        verify(obs, never()).onError(any());
        verify(obs, never()).onCatchup();
        verify(obs, never()).onNext(anyInt());
    }

    @Test
    void testOnCatchupDoesNotClose() {
        SubscriptionImpl<Integer> on = SubscriptionImpl.on(obs);
        on.notifyCatchup();
        on.notifyElement(1);
        on.notifyError(new Throwable("ignore me"));
        verify(obs).onError(any());
        verify(obs).onCatchup();
        verify(obs).onNext(anyInt());
    }

    @Test
    void testOnErrorCompletesFutureCatchup() {
        Assertions.assertThrows(SubscriptionCancelledException.class, () -> {
            SubscriptionImpl<Integer> on = SubscriptionImpl.on(obs);
            verify(obs, never()).onError(any());
            on.notifyError(new Throwable("ignore me"));
            verify(obs).onError(any());
            on.awaitCatchup();
        });
    }

    @Test
    void testOnErrorCompletesFutureComplete() {
        Assertions.assertThrows(SubscriptionCancelledException.class, () -> {
            SubscriptionImpl<Integer> on = SubscriptionImpl.on(obs);
            verify(obs, never()).onError(any());
            on.notifyError(new Throwable("ignore me"));
            verify(obs).onError(any());
            on.awaitComplete();
        });
    }

    @Test
    void testAwaitCatchupLong() throws Exception {
        Assertions.assertTimeout(Duration.ofMillis(100), () -> {
            uut.notifyCatchup();
            uut.awaitCatchup(100000);
        });
    }

    @Test
    void testAwaitCompleteLong() throws Exception {
        Assertions.assertTimeout(Duration.ofMillis(100), () -> {
            uut.notifyComplete();
            uut.awaitComplete(100000);
        });
    }

    @Test
    public void testCloseThrowsException() throws Exception {
        uut = spy(uut);
        doThrow(RuntimeException.class).when(uut).close();

        // this must return without exceptions
        uut.notifyComplete();
    }
}
