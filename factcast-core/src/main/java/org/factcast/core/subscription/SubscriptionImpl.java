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
package org.factcast.core.subscription;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.factcast.core.subscription.observer.GenericObserver;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Implements a subscription and offers notifyX methods for the Fact Supplier to
 * write to.
 *
 * @author <uwe.schaefer@mercateo.com>
 *
 * @param <T>
 */
@RequiredArgsConstructor
@Slf4j
public class SubscriptionImpl<T> implements Subscription {

    @NonNull
    final GenericObserver<T> observer;

    @NonNull
    Runnable onClose = () -> {
    };

    final AtomicBoolean closed = new AtomicBoolean(false);

    final CompletableFuture<Void> catchup = new CompletableFuture<>();

    final CompletableFuture<Void> complete = new CompletableFuture<>();

    @Override
    public void close() {
        closed.set(true);
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
        if (!closed.get()) {
            observer.onCatchup();
            if (!catchup.isDone()) {
                catchup.complete(null);
            }
        }
    }

    @SuppressFBWarnings("NP_NONNULL_PARAM_VIOLATION")
    public void notifyComplete() {
        if (!closed.get()) {
            observer.onComplete();
            if (!catchup.isDone()) {
                catchup.complete(null);
            }
            if (!complete.isDone()) {
                complete.complete(null);
            }
            tryClose();
        }
    }

    public void notifyError(Throwable e) {
        if (!closed.get()) {
            observer.onError(e);
            if (!catchup.isDone()) {
                catchup.completeExceptionally(e);
            }
            if (!complete.isDone()) {
                complete.completeExceptionally(e);
            }
            tryClose();
        }
    }

    private void tryClose() {
        try {
            close();
        } catch (Exception e) {
            log.trace("Irrelevant Excption during close: ", e);
        }
    }

    public void notifyElement(@NonNull T e) {
        if (!closed.get()) {
            observer.onNext(e);
        }
    }

    public SubscriptionImpl<T> onClose(Runnable e) {
        onClose = e;
        return this;
    }

    public static <T> SubscriptionImpl<T> on(@NonNull GenericObserver<T> o) {
        return new SubscriptionImpl<>(o);
    }
}
