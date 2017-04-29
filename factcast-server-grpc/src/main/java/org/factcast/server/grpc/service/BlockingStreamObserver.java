package org.factcast.server.grpc.service;

import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;

//TODO document
@Slf4j
class BlockingStreamObserver<T> implements StreamObserver<T> {

    private final ServerCallStreamObserver<T> delegate;

    private final Object lock = new Object();

    BlockingStreamObserver(ServerCallStreamObserver<T> delegate) {
        this.delegate = delegate;
        this.delegate.setOnReadyHandler(() -> {
            synchronized (lock) {
                lock.notifyAll(); // wake up our thread
            }
        });
        this.delegate.setOnCancelHandler(() -> {
            synchronized (lock) {
                lock.notifyAll(); // wake up our thread
            }
        });
        delegate.setCompression("gzip");
        delegate.setMessageCompression(true);

    }

    @Override
    public void onNext(T value) {
        synchronized (lock) {

            if (!delegate.isReady()) {

                for (int i = 0; i < 5; i++) {
                    log.debug("Channel not ready. Slow client? Waiting");
                    try {
                        lock.wait(5000);
                    } catch (InterruptedException meh) {
                    }
                    if (delegate.isReady()) {
                        break;
                    } else {
                        delegate.isCancelled();
                        throw new RuntimeException("channel was cancelled.");
                    }
                }

                if (!delegate.isReady()) {
                    throw new RuntimeException("channel not coming back.");
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