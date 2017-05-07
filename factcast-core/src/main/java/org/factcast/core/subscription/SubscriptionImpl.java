package org.factcast.core.subscription;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.factcast.core.subscription.observer.GenericObserver;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
public class SubscriptionImpl<T> implements Subscription {

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
    public Subscription awaitCatchup() throws SubscriptionCancelledException {
        try {
            catchup.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new SubscriptionCancelledException(e);
        }
        return this;

    }

    public Subscription awaitCatchup(long waitTimeInMillis) throws SubscriptionCancelledException,
            TimeoutException {
        try {
            catchup.get(waitTimeInMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException | ExecutionException e) {
            throw new SubscriptionCancelledException(e);
        }
        return this;

    }

    @Override
    public Subscription awaitComplete() throws SubscriptionCancelledException {
        try {
            complete.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new SubscriptionCancelledException(e);
        }
        return this;
    }

    public Subscription awaitComplete(long waitTimeInMillis) throws SubscriptionCancelledException,
            TimeoutException {
        try {
            complete.get(waitTimeInMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException | ExecutionException e) {
            throw new SubscriptionCancelledException(e);
        }
        return this;
    }

    @SuppressFBWarnings("NP_NONNULL_PARAM_VIOLATION")
    public void notifyCatchup() {
        observer.onCatchup();
        if (!catchup.isDone()) {
            catchup.complete(null);
        }
    }

    @SuppressFBWarnings("NP_NONNULL_PARAM_VIOLATION")
    public void notifyComplete() {
        observer.onComplete();
        if (!catchup.isDone()) {
            catchup.complete(null);
        }

        if (!complete.isDone()) {
            complete.complete(null);
        }
        tryClose();
    }

    public void notifyError(Throwable e) {
        observer.onError(e);
        if (!catchup.isDone()) {
            catchup.completeExceptionally(e);
        }
        if (!complete.isDone()) {
            complete.completeExceptionally(e);
        }
        tryClose();
    }

    private void tryClose() {
        try {
            close();
        } catch (Exception e) {
            log.trace("Irrelevant Excption during close: ", e);
        }
    }

    public void notifyElement(@NonNull T e) {
        observer.onNext(e);
    }

    public SubscriptionImpl<T> onClose(Runnable e) {
        onClose = e;
        return this;
    }

    public static <T> SubscriptionImpl<T> on(@NonNull GenericObserver<T> o) {
        return new SubscriptionImpl<>(o);
    }
}
