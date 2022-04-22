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
package org.factcast.client.grpc;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;
import io.grpc.Status;
import io.grpc.Status.Code;
import io.grpc.StatusRuntimeException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.Fact;
import org.factcast.core.store.RetryableException;
import org.factcast.core.subscription.FactStreamInfo;
import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.SubscriptionCancelledException;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.core.subscription.observer.FactObserver;
import org.factcast.core.util.ExceptionHelper;

@Slf4j
public class ResilientGrpcSubscription implements Subscription {
  // TODO configurable?
  private static final int ALLOWED_TIME_BETWEEN_RECONNECTS = 3000;
  // TODO configurable?
  private static final int ALLOWED_NUMBER_OF_RECONNECTS_BEFORE_ESCALATION = 5;
  private static final Set<Code> RETRYABLE_STATUS =
      Sets.newHashSet(
          Status.UNKNOWN.getCode(), //
          Status.UNAVAILABLE.getCode(), //
          Status.ABORTED.getCode());

  private final GrpcFactStore store;
  private final SubscriptionRequestTO originalRequest;
  private final FactObserver originalObserver;
  private final FactObserver delegatingObserver;

  private final AtomicReference<UUID> lastFactIdSeen = new AtomicReference<>();
  private final AtomicReference<Subscription> currentSubscription = new AtomicReference<>();
  private final AtomicBoolean isClosed = new AtomicBoolean(false);
  private final List<Long> timestampsOfReconnectionAttempts = new ArrayList<>();

  public ResilientGrpcSubscription(
      @NonNull GrpcFactStore store, @NonNull SubscriptionRequestTO req, @NonNull FactObserver obs) {
    this.store = store;
    originalObserver = obs;
    originalRequest = req;
    delegatingObserver = new DelegatingFactObserver();
    connect();
  }

  @Override
  public Subscription awaitCatchup() throws SubscriptionCancelledException {
    return delegate(Subscription::awaitCatchup);
  }

  @Override
  public Subscription awaitCatchup(long waitTimeInMillis)
      throws SubscriptionCancelledException, TimeoutException {
    return delegate(Subscription::awaitCatchup, waitTimeInMillis);
  }

  @Override
  public Subscription awaitComplete() throws SubscriptionCancelledException {
    return delegate(Subscription::awaitComplete);
  }

  @Override
  public Subscription awaitComplete(long waitTimeInMillis)
      throws SubscriptionCancelledException, TimeoutException {
    return delegate(Subscription::awaitComplete, waitTimeInMillis);
  }

  @Override
  public void close() {
    try {
      closeAndDetachSubscription();
    } finally {
      isClosed.set(true);
    }
  }

  private ResilientGrpcSubscription delegate(
      ThrowingBiConsumer<Subscription, Long> consumer, long waitTimeInMillis)
      throws TimeoutException {
    long startTime = System.currentTimeMillis();
    for (; ; ) {
      assertSubscriptionStateNotClosed();
      Subscription cur = currentSubscription.get();

      if (cur != null) {

        try {
          consumer.accept(cur, waitTimeInMillis);
          return this;
        } catch (Exception e) {
          if (!shouldRetry(e)) {
            throw e;
          }
        }
      } else {
        sleep(100);
      }
      if ((System.currentTimeMillis() - startTime) > waitTimeInMillis) {
        throw new TimeoutException();
      }
    }
  }

  private ResilientGrpcSubscription delegate(Consumer<Subscription> consumer) {
    for (; ; ) {
      assertSubscriptionStateNotClosed();
      Subscription cur = currentSubscription.get();
      if (cur != null) {
        try {
          consumer.accept(cur);
          return this;
        } catch (Exception e) {
          if (!shouldRetry(e)) {
            throw e;
          }
        }
      } else {
        // wait for a new subscription to be created
        // TODO maybe replace by currSub.wait?
        sleep(100);
      }
    }
  }

  private boolean attemptsExhausted() {
    int attempts = numberOfAttemptsInWindow();
    return attempts > ALLOWED_NUMBER_OF_RECONNECTS_BEFORE_ESCALATION;
  }

  private int numberOfAttemptsInWindow() {
    long now = System.currentTimeMillis();
    // remove all older reconnection attempts
    timestampsOfReconnectionAttempts.removeIf(t -> now - t > ALLOWED_TIME_BETWEEN_RECONNECTS);
    return timestampsOfReconnectionAttempts.size();
  }

  private void registerAttempt() {
    timestampsOfReconnectionAttempts.add(System.currentTimeMillis());
  }

  private boolean shouldRetry(Throwable exception) {
    return isRetryable(exception) && !attemptsExhausted();
  }

  private void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException ignore) {
    }
  }

  private void assertSubscriptionStateNotClosed() {
    if (isClosed.get()) {
      throw new SubscriptionCancelledException("Subscription already cancelled");
    }
  }

  @VisibleForTesting
  boolean isRetryable(@NonNull Throwable exception) {
    if (exception instanceof StatusRuntimeException) {
      Code s = ((StatusRuntimeException) exception).getStatus().getCode();
      return RETRYABLE_STATUS.contains(s);
    }
    // TODO anything else?
    //    if (exception instanceof StaleSubscriptionDetectedException) {
    //      // assume connection problem
    //      return false;
    //    }

    if (exception instanceof RetryableException) return true;

    return false;
  }

  private synchronized void connect() {
    SubscriptionRequestTO to = SubscriptionRequestTO.forFacts(originalRequest);
    UUID last = lastFactIdSeen.get();
    if (last != null) {
      to.startingAfter(last);
    }

    if (currentSubscription.get() == null) {
      // might throw exceptions TODO
      Subscription plainSubscription = store.internalSubscribe(to, delegatingObserver);
      boolean settingSucceeded = currentSubscription.compareAndSet(null, plainSubscription);
      if (!settingSucceeded) {
        // TODO something weird is going on here
        try {
          plainSubscription.close();
        } catch (Throwable ignore) {
        }
        throw new IllegalStateException("TODO");
      }
    }
  }

  private void closeAndDetachSubscription() {
    Subscription current = currentSubscription.getAndSet(null);
    try {
      if (current != null) current.close();
    } catch (Exception justLog) {
      log.warn("Ignoring Exception while closing a subscription:", justLog);
    }
  }

  @FunctionalInterface
  private interface ThrowingBiConsumer<T, U> {
    void accept(T t, U u) throws TimeoutException;
  }

  class DelegatingFactObserver implements FactObserver {
    @Override
    public void onNext(@NonNull Fact element) {
      if (!isClosed.get()) {
        originalObserver.onNext(element);
        lastFactIdSeen.set(element.id());
      } else {
        log.warn("Fact arrived after call to .close() [a few of them is ok...]");
      }
    }

    @Override
    public void onCatchup() {
      originalObserver.onCatchup();
    }

    @Override
    public void onComplete() {
      originalObserver.onComplete();
    }

    @Override
    public void onFastForward(@NonNull UUID factIdToFfwdTo) {
      originalObserver.onFastForward(factIdToFfwdTo);
    }

    @Override
    public void onError(@NonNull Throwable exception) {
      log.info("Closing subscription due to onError triggered.", exception);
      closeAndDetachSubscription();

      registerAttempt();

      if (shouldRetry(exception)) {
        log.debug("Pausing 100ms before reconnect");
        sleep(100);
        log.info("Trying to reconnect.");

        connect();
        // TODO log recorded exceptions?

      } else {
        // give up
        originalObserver.onError(exception);
        throw ExceptionHelper.toRuntime(exception);
      }
    }

    @Override
    public void onFactStreamInfo(FactStreamInfo info) {
      originalObserver.onFactStreamInfo(info);
    }
  }
}
