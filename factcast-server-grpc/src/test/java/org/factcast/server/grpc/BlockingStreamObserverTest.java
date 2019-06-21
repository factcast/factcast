/*
 * Copyright © 2018 Mercateo AG (http://www.mercateo.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.factcast.server.grpc;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.*;
import org.mockito.junit.jupiter.*;

import io.grpc.stub.*;

@ExtendWith(MockitoExtension.class)
public class BlockingStreamObserverTest {

    @Mock
    private ServerCallStreamObserver<Object> delegate;

    private BlockingStreamObserver<Object> uut;

    @BeforeEach
    void setUp() {
        uut = new BlockingStreamObserver<>("foo", delegate);
    }

    @Test
    void testOnCompleted() {
        verify(delegate, never()).onCompleted();
        uut.onCompleted();
        verify(delegate).onCompleted();
    }

    @Test
    void testNullContract() {
        assertThrows(NullPointerException.class, () -> {
            new BlockingStreamObserver(null, mock(ServerCallStreamObserver.class));
        });
        assertThrows(NullPointerException.class, () -> {
            new BlockingStreamObserver(null, null);
        });
        assertThrows(NullPointerException.class, () -> {
            new BlockingStreamObserver("oink", null);
        });
    }

    @Test
    void testOnError() {
        verify(delegate, never()).onError(any());
        uut.onError(new Exception());
        verify(delegate).onError(any());
    }

    @Test
    void testOnNextWhenReady() {
        when(delegate.isReady()).thenReturn(true);
        uut.onNext(new Object());
        verify(delegate).onNext(any());
    }

    @Test
    void testOnNextNotYetReady() throws Exception {
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
    void testOnNextNotReadyThenCancelled() throws Exception {
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
