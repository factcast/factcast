/**
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

import com.google.common.annotations.VisibleForTesting;

import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * StreamObserver impl that blocks if the Stream to the consumer is not in
 * writeable state to provide a basic backpressure alike property.
 *
 * Note it the consumer stream is not writeable, the
 * {@link BlockingStreamObserver} will retry RETRY_COUNT (default 60) times
 * after WAIT_TIME (default 1000) millis
 *
 * @author <uwe.schaefer@mercateo.com>
 *
 * @param <T>
 */
@Slf4j
class BlockingStreamObserver<T> implements StreamObserver<T> {

    static final int RETRY_COUNT = 60;

    static final int WAIT_TIME = 1000;

    final ServerCallStreamObserver<T> delegate;

    final Object lock = new Object();

    final String id;

    BlockingStreamObserver(@NonNull String id, @NonNull ServerCallStreamObserver<T> delegate) {
        this.id = id;
        this.delegate = delegate;
        this.delegate.setOnReadyHandler(this::wakeup);
        this.delegate.setOnCancelHandler(this::wakeup);
        delegate.setCompression("gzip");
        delegate.setMessageCompression(true);
    }

    @VisibleForTesting
    void wakeup() {
        synchronized (lock) {
            // wake up our thread
            lock.notifyAll();
        }
    }

    @Override
    public void onNext(T value) {
        if (!delegate.isCancelled()) {
            synchronized (lock) {
                if (!delegate.isReady()) {
                    for (int i = 1; i <= RETRY_COUNT; i++) {
                        log.debug("{} channel not ready. Slow client? Attempt: {}/{}", id, i,
                                RETRY_COUNT);
                        try {
                            lock.wait(WAIT_TIME);
                        } catch (InterruptedException meh) {
                            // ignore
                        }
                        if (delegate.isReady()) {
                            break;
                        }
                        if (delegate.isCancelled()) {
                            // channel was cancelled.
                            break;
                        }
                    }
                    if (!delegate.isReady() && !delegate.isCancelled()) {
                        throw new TransportLayerException("channel not coming back.");
                    }
                }
                if (!delegate.isCancelled())
                    delegate.onNext(value);
            }
        }
    }

    @Override
    public void onError(Throwable t) {
        delegate.onError(t);
    }

    @Override
    public void onCompleted() {
        delegate.onCompleted();
    }
}
