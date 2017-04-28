package org.factcast.core.subscription;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class SubscriptionImpl<T> implements Subscription {

    @NonNull
    final GenericObserver<T> observer;

    @NonNull
    Runnable onClose = () -> {
    };

    final CompletableFuture<Void> catchup = new CompletableFuture<Void>();

    final CompletableFuture<Void> complete = new CompletableFuture<Void>();

    @Override
    public void close() throws Exception {
        SubscriptionCancelledException closedException = new SubscriptionCancelledException(
                "Client closed the subscription");
        catchup.completeExceptionally(closedException);
        complete.completeExceptionally(closedException);
        onClose.run();
    }

    @Override
    public void awaitCatchup() throws SubscriptionCancelledException {
        try {
            catchup.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new SubscriptionCancelledException(e);
        }
    }

    @Override
    public void awaitComplete() throws SubscriptionCancelledException {
        try {
            complete.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new SubscriptionCancelledException(e);
        }
    }

    public void notifyCatchup() {
        observer.onCatchup();
        if (!catchup.isDone()) {
            catchup.complete(null);
        }
    }

    public void notifyComplete() {
        observer.onComplete();
        if (!complete.isDone()) {
            complete.complete(null);
        }
    }

    public void notifyError(Throwable e) {
        observer.onError(e);
        if (!catchup.isDone()) {
            catchup.completeExceptionally(e);
        }
        if (!complete.isDone()) {
            complete.completeExceptionally(e);
        }
    }

    public void notifyElement(T e) {
        observer.onNext(e);
    }

    public SubscriptionImpl<T> onClose(Runnable e) {
        onClose = e;
        return this;
    }
}
