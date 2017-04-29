package org.factcast.server.grpc.service;

import java.util.UUID;

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

    BlockingStreamObserver(ServerCallStreamObserver<T> delegate) {
        this.delegate = delegate;
        this.delegate.setOnReadyHandler(this::wakeup);
        this.delegate.setOnCancelHandler(this::wakeup);
        delegate.setCompression("gzip");
        delegate.setMessageCompression(true);
    }

    private void wakeup() {
        synchronized (lock) {
            lock.notifyAll(); // wake up our thread
        }
    }

    @Override
    public void onNext(T value) {
        synchronized (lock) {

            if (!delegate.isReady()) {
                UUID ticket = UUID.randomUUID();
                for (int i = 1; i <= retry; i++) {
                    log.debug("Channel not ready. Slow client? Waiting. Ticket: {}, Attempt: {}",
                            ticket, i);
                    try {
                        lock.wait(waitTime);
                    } catch (InterruptedException meh) {
                    }
                    if (delegate.isReady()) {
                        break;
                    }
                    if (delegate.isCancelled()) {
                        throw new RuntimeException("channel was cancelled. Ticket: " + ticket);
                    }
                }
                if (!delegate.isReady()) {
                    throw new RuntimeException("channel not coming back. Ticket: " + ticket);
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