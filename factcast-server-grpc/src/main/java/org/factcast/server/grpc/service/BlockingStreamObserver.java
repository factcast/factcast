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

        if (delegate.isCancelled()) {
            return;
        }

        synchronized (lock) {
            while (!delegate.isReady()) {
                try {
                    while (!delegate.isCancelled() && !delegate.isReady()) {
                        log.debug("slow client, waiting");
                        lock.wait(30000);
                    }

                    if (delegate.isCancelled()) {
                        return;
                    }

                } catch (InterruptedException meh) {
                    // ignore
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