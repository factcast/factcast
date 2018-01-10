package org.factcast.server.rest.resources;

import java.util.concurrent.CompletableFuture;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ConnectionCleanupRunnable implements Runnable {

    private final FactsObserver observer;

    private final CompletableFuture<Void> future;

    public ConnectionCleanupRunnable(@NonNull FactsObserver observer,
            @NonNull CompletableFuture<Void> future) {
        this.observer = observer;
        this.future = future;
    }

    @Override
    public void run() {
        if (!observer.isConnectionAlive()) {
            observer.unsubscribe();
            future.complete(null);
            log.debug("unsubscribe pending connection");
        }

    }

}
