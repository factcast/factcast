/*
 * Copyright © 2017-2020 factcast.org
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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.Fact;
import org.factcast.core.subscription.observer.FactObserver;
import org.factcast.core.subscription.observer.GenericObserver;

/**
 * Implements a subscription and offers notifyX methods for the Fact Supplier to write to.
 *
 * @author <uwe.schaefer@prisma-capacity.eu>
 */
@RequiredArgsConstructor
@Slf4j
public class SubscriptionImpl implements Subscription {

  @NonNull final GenericObserver<Fact> observer;

  @NonNull final FactTransformers transformers;

  @NonNull Runnable onClose = () -> {};

  final AtomicBoolean closed = new AtomicBoolean(false);

  final CompletableFuture<Void> catchup = new CompletableFuture<>();

  final CompletableFuture<Void> complete = new CompletableFuture<>();

  @Override
  public void close() {
    if (!closed.getAndSet(true)) {
      SubscriptionCancelledException closedException =
          new SubscriptionCancelledException("Client closed the subscription");
      catchup.completeExceptionally(closedException);
      complete.completeExceptionally(closedException);
      onClose.run();
    }
  }

  @Override
  public Subscription awaitCatchup() throws SubscriptionCancelledException {
    try {
      catchup.get();
    } catch (InterruptedException e) {
      throw new SubscriptionCancelledException(e);
    } catch (ExecutionException e) {
      throw new SubscriptionCancelledException(e.getCause());
    }
    return this;
  }

  @Override
  public Subscription awaitCatchup(long waitTimeInMillis)
      throws SubscriptionCancelledException, TimeoutException {
    try {
      catchup.get(waitTimeInMillis, TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      throw new SubscriptionCancelledException(e);
    } catch (ExecutionException e) {
      throw new SubscriptionCancelledException(e.getCause());
    }
    return this;
  }

  @Override
  public Subscription awaitComplete() throws SubscriptionCancelledException {
    try {
      complete.get();
    } catch (InterruptedException e) {
      throw new SubscriptionCancelledException(e);
    } catch (ExecutionException e) {
      throw new SubscriptionCancelledException(e.getCause());
    }
    return this;
  }

  @Override
  public Subscription awaitComplete(long waitTimeInMillis)
      throws SubscriptionCancelledException, TimeoutException {
    try {
      complete.get(waitTimeInMillis, TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      throw new SubscriptionCancelledException(e);
    } catch (ExecutionException e) {
      throw new SubscriptionCancelledException(e.getCause());
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

  public void notifyFastForward(@NonNull UUID factId) {
    if (!closed.get()) {
      observer.onFastForward(factId);
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
      if (!catchup.isDone()) {
        catchup.completeExceptionally(e);
      }
      if (!complete.isDone()) {
        complete.completeExceptionally(e);
      }
      observer.onError(e);

      tryClose();
    }
  }

  private void tryClose() {
    try {
      close();
    } catch (Exception e) {
      log.trace("Irrelevant Exception during close: ", e);
    }
  }

  public void notifyElement(@NonNull Fact e) throws TransformationException {
    if (!closed.get()) {
      observer.onNext(transformers.transformIfNecessary(e));
    }
  }

  public SubscriptionImpl onClose(Runnable e) {
    onClose = e;
    return this;
  }

  public static SubscriptionImpl on(
      @NonNull GenericObserver<org.factcast.core.Fact> o, FactTransformers transformers) {
    return new SubscriptionImpl(o, transformers);
  }

  // for client side
  public static SubscriptionImpl on(@NonNull FactObserver observer2) {
    return new SubscriptionImpl(observer2, e -> e);
  }
}
