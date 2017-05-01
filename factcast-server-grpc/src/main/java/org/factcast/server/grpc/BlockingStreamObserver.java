package org.factcast.server.grpc;

import com.google.common.annotations.VisibleForTesting;

import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;

//TODO document
@Slf4j
class BlockingStreamObserver<T> implements StreamObserver<T> {

    static final int retry = 60;

    static final int waitTime = 1000;

    final ServerCallStreamObserver<T> delegate;

    final Object lock = new Object();

    final String id;

    BlockingStreamObserver(String id, ServerCallStreamObserver<T> delegate) {
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
            lock.notifyAll(); // wake up our thread
        }
    }

    @Override
    public void onNext(T value) {
        synchronized (lock) {

            if (!delegate.isReady()) {

                for (int i = 1; i <= retry; i++) {
                    log.debug("{} channel not ready. Slow client? Attempt: {}/{}", id, i, retry);
                    try {
                        lock.wait(waitTime);
                    } catch (InterruptedException meh) {
                    }
                    if (delegate.isReady()) {
                        break;
                    }
                    if (delegate.isCancelled()) {
                        throw new TransportLayerException("channel was cancelled.");
                    }
                }
                if (!delegate.isReady()) {
                    throw new TransportLayerException("channel not coming back.");
                }
            }
        }
        delegate.onNext(value);
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