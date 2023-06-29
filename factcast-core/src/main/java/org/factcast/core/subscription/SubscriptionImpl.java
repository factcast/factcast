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
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.Fact;
import org.factcast.core.subscription.observer.FactObserver;
import org.factcast.core.util.ExceptionHelper;

/**
 * Implements a subscription and offers notifyX methods for the Fact Supplier to write to.
 *
 * @author <uwe.schaefer@prisma-capacity.eu>
 */
@RequiredArgsConstructor
@Slf4j
public class SubscriptionImpl implements InternalSubscription {

  @NonNull final FactObserver observer;

  @NonNull Runnable onClose = () -> {};

  final AtomicBoolean closed = new AtomicBoolean(false);

  final CompletableFuture<Void> catchup = new CompletableFuture<>();

  final CompletableFuture<Void> complete = new CompletableFuture<>();

  @Override
  public void close() {
    if (!closed.getAndSet(true)) {
      SubscriptionClosedException closedException =
          new SubscriptionClosedException("Client closed the subscription");
      catchup.completeExceptionally(closedException);
      complete.completeExceptionally(closedException);
      onClose.run();
    }
  }

  @Override
  public Subscription awaitCatchup() throws SubscriptionClosedException {
    return await(catchup::get);
  }

  @Override
  public Subscription awaitCatchup(long waitTimeInMillis)
      throws SubscriptionClosedException, TimeoutException {
    return awaitTimed(() -> catchup.get(waitTimeInMillis, TimeUnit.MILLISECONDS));
  }

  @Override
  public Subscription awaitComplete() throws SubscriptionClosedException {
    return await(complete::get);
  }

  @Override
  public Subscription awaitComplete(long waitTimeInMillis)
      throws SubscriptionClosedException, TimeoutException {
    return awaitTimed(() -> complete.get(waitTimeInMillis, TimeUnit.MILLISECONDS));
  }

  @FunctionalInterface
  interface ThrowingRunnable {
    void run() throws InterruptedException, ExecutionException;
  }

  @FunctionalInterface
  interface ThrowingTimedRunnable {
    void run() throws InterruptedException, ExecutionException, TimeoutException;
  }

  private Subscription await(ThrowingRunnable o) {
    try {
      o.run();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new SubscriptionClosedException(e);
    } catch (ExecutionException e) {
      throw ExceptionHelper.toRuntime(e.getCause());
    }
    return this;
  }

  private Subscription awaitTimed(ThrowingTimedRunnable o) throws TimeoutException {
    try {
      o.run();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new SubscriptionClosedException(e);
    } catch (ExecutionException e) {
      throw ExceptionHelper.toRuntime(e.getCause());
    }
    return this;
  }

  @Override
  @SuppressFBWarnings("NP_NONNULL_PARAM_VIOLATION")
  public void notifyCatchup() {
    if (!closed.get()) {
      observer.onCatchup();
      if (!catchup.isDone()) {
        catchup.complete(null);
      }
    }
  }

  @Override
  public void notifyFastForward(@NonNull UUID factId) {
    if (!closed.get()) {
      observer.onFastForward(factId);
    }
  }

  @Override
  public void notifyFactStreamInfo(@NonNull FactStreamInfo info) {
    if (!closed.get()) {
      observer.onFactStreamInfo(info);
    }
  }

  @Override
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

  @Override
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

  @Override
  public void notifyElement(@NonNull Fact e) throws TransformationException {
    if (!closed.get()) {
      // note that this fact is already transformed
      observer.onNext(e);
    }
  }

  @Override
  public void notifyElements(@NonNull List<Fact> batch) throws TransformationException {
    if (!closed.get()) {
      // note that those facts are already transformed
      observer.onNext(batch);
    }
  }

  @Override
  public SubscriptionImpl onClose(Runnable e) {
    Runnable formerOnClose = onClose;
    onClose =
        () -> {
          tryRun(formerOnClose);
          tryRun(e);
        };
    return this;
  }

  private void tryRun(Runnable e) {
    try {
      e.run();
    } catch (Exception ex) {
      log.error("While executing onClose:", ex);
    }
  }

  public static SubscriptionImpl on(@NonNull FactObserver o) {
    return new SubscriptionImpl(o);
  }
}
