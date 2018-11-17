package org.factcast.server.grpc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.grpc.stub.ServerCallStreamObserver;

@ExtendWith(MockitoExtension.class)
public class BlockingStreamObserver0Test {

    @Mock
    private ServerCallStreamObserver<Object> delegate;

    private BlockingStreamObserver<Object> uut;

    @BeforeEach
    public void setUp() {
        uut = new BlockingStreamObserver<>("foo", delegate);
    }

    @Test
    public void testOnCompleted() {
        verify(delegate, never()).onCompleted();
        uut.onCompleted();
        verify(delegate).onCompleted();
    }

    @Test
    public void testOnError() {
        verify(delegate, never()).onError(any());
        uut.onError(new Exception());
        verify(delegate).onError(any());
    }

    @Test
    public void testOnNextWhenReady() {
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
        Thread.sleep(100);
        assertFalse(onNextCall.isDone());
        cancelled.set(true);
        uut.wakeup();
        Thread.sleep(100);
        assertTrue(onNextCall.isDone());
        verify(delegate, never()).onNext(any());
    }
}
