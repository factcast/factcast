package org.factcast.server.grpc;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

import io.grpc.stub.ServerCallStreamObserver;

@RunWith(org.mockito.junit.MockitoJUnitRunner.class)
public class BlockingStreamObserver0Test {
    @Mock
    private ServerCallStreamObserver<Object> delegate;

    private BlockingStreamObserver<Object> uut;

    @Before
    public void setUp() throws Exception {
        uut = new BlockingStreamObserver<>("foo", delegate);
    }

    @Test
    public void testOnCompleted() throws Exception {
        verify(delegate, never()).onCompleted();
        uut.onCompleted();
        verify(delegate).onCompleted();
    }

    @Test
    public void testOnError() throws Exception {
        verify(delegate, never()).onError(any());
        uut.onError(new Exception());
        verify(delegate).onError(any());
    }

    @Test
    public void testOnNextWhenReady() throws Exception {
        when(delegate.isReady()).thenReturn(true);
        uut.onNext(new Object());
        verify(delegate).onNext(any());
    }

    @Test
    public void testOnNextNotYetReady() throws Exception {

        AtomicBoolean ready = new AtomicBoolean(false);

        when(delegate.isReady()).thenAnswer(i -> ready.get());
        CompletableFuture<Void> onNextCall = CompletableFuture.runAsync(() -> uut.onNext(
                new Object()));

        Thread.sleep(30);
        assertFalse(onNextCall.isDone());

        ready.set(true);
        uut.wakeup();

        Thread.sleep(30);
        assertTrue(onNextCall.isDone());

        verify(delegate).onNext(any());
    }

    @Test
    public void testOnNextNotReadyThenCancelled() throws Exception {

        AtomicBoolean ready = new AtomicBoolean(false);
        AtomicBoolean cancelled = new AtomicBoolean(false);

        when(delegate.isReady()).thenAnswer(i -> ready.get());
        when(delegate.isCancelled()).thenAnswer(i -> cancelled.get());

        CompletableFuture<Void> onNextCall = CompletableFuture.runAsync(() -> uut.onNext(
                new Object()));

        Thread.sleep(30);
        assertFalse(onNextCall.isDone());

        cancelled.set(true);
        uut.wakeup();

        Thread.sleep(30);
        assertTrue(onNextCall.isDone());

        verify(delegate, never()).onNext(any());
    }
}
