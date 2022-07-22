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

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;

import org.factcast.client.grpc.FactCastGrpcClientProperties.ResilienceConfiguration;
import org.factcast.core.Fact;
import org.factcast.core.subscription.*;
import org.factcast.core.subscription.observer.FactObserver;
import org.factcast.core.util.ExceptionHelper;

import com.google.common.annotations.VisibleForTesting;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ResilientGrpcSubscription implements InternalSubscription {

  private final GrpcFactStore store;
  private final SubscriptionRequestTO originalRequest;
  private final FactObserver originalObserver;
  private final FactObserver delegatingObserver;

  private final AtomicReference<UUID> lastFactIdSeen = new AtomicReference<>();
  private final SubscriptionHolder currentSubscription = new SubscriptionHolder();
  private final AtomicBoolean isClosed = new AtomicBoolean(false);

  @Getter @VisibleForTesting final Resilience resilience;

  public ResilientGrpcSubscription(
      @NonNull GrpcFactStore store,
      @NonNull SubscriptionRequestTO req,
      @NonNull FactObserver obs,
      @NonNull ResilienceConfiguration config) {
    this.store = store;
    resilience = new Resilience(config);
    originalObserver = obs;
    originalRequest = req;
    delegatingObserver = new DelegatingFactObserver();
    connect();
  }

  @Override
  public Subscription awaitCatchup() throws SubscriptionClosedException {
    return delegate(Subscription::awaitCatchup);
  }

  @Override
  public Subscription awaitCatchup(long waitTimeInMillis)
      throws SubscriptionClosedException, TimeoutException {
    return delegate(Subscription::awaitCatchup, waitTimeInMillis);
  }

  @Override
  public Subscription awaitComplete() throws SubscriptionClosedException {
    return delegate(Subscription::awaitComplete);
  }

  @Override
  public Subscription awaitComplete(long waitTimeInMillis)
      throws SubscriptionClosedException, TimeoutException {
    return delegate(Subscription::awaitComplete, waitTimeInMillis);
  }

  @Override
  public void close() {
    if (!isClosed.getAndSet(true)) {
      try {
        closeAndDetachSubscription();
      } finally {
        isClosed.set(true);
      }
    }
  }

  @Override
  public void notifyCatchup() {}

  @Override
  public void notifyFastForward(@NonNull UUID factId) {}

  @Override
  public void notifyFactStreamInfo(@NonNull FactStreamInfo info) {}

  @Override
  public void notifyComplete() {}

  @Override
  public void notifyError(Throwable e) {}

  @Override
  public void notifyElement(@NonNull Fact e) throws TransformationException {}

  @Override
  public SubscriptionImpl onClose(Runnable e) {
    return null;
  }

  @Override
  // TODO remove
  public AtomicLong factsNotTransformed() {
    return currentSubscription.get().factsNotTransformed();
  }

  @Override
  // TODO remove
  public AtomicLong factsTransformed() {
    return currentSubscription.get().factsTransformed();
  }

  @VisibleForTesting
  ResilientGrpcSubscription delegate(
      ThrowingBiConsumer<Subscription, Long> consumer, long waitTimeInMillis)
      throws TimeoutException {
    long startTime = System.currentTimeMillis();
    for (; ; ) {
      assertSubscriptionStateNotClosed();
      long maxPause = waitTimeInMillis - (System.currentTimeMillis() - startTime);

      try {
        Subscription cur = currentSubscription.getAndBlock(maxPause);
        consumer.accept(cur, maxPause);
        return this;
      } catch (TimeoutException t) {
        throw t;
      } catch (Exception e) {
        if (!resilience.shouldRetry(e)) {
          throw e;
        }
      }
      if ((System.currentTimeMillis() - startTime) > waitTimeInMillis) {
        throw new TimeoutException();
      }
    }
  }

  @VisibleForTesting
  ResilientGrpcSubscription delegate(Consumer<Subscription> consumer) {
    for (; ; ) {
      assertSubscriptionStateNotClosed();
      try {
        Subscription cur = currentSubscription.getAndBlock();
        consumer.accept(cur);
        return this;
      } catch (Exception e) {
        if (!resilience.shouldRetry(e)) {
          throw ExceptionHelper.toRuntime(e);
        }
      }
    }
  }

  private void assertSubscriptionStateNotClosed() {
    if (isClosed.get()) {
      throw new SubscriptionClosedException(
          "Subscription already closed  (" + originalRequest + ")");
    }
  }

  private synchronized void connect() {
    log.debug("Connecting ({})", originalRequest);
    doConnect();
  }

  @VisibleForTesting
  synchronized void reConnect() {
    log.debug("Reconnecting ({})", originalRequest);
    doConnect();
  }

  private void doConnect() {
    resilience.registerAttempt();
    SubscriptionRequestTO to = SubscriptionRequestTO.forFacts(originalRequest);
    UUID last = lastFactIdSeen.get();
    if (last != null) {
      to.startingAfter(last);
    }

    if (currentSubscription.get() == null) {
      try {
        InternalSubscription plainSubscription = store.internalSubscribe(to, delegatingObserver);
        currentSubscription.set(plainSubscription);
      } catch (Exception e) {
        fail(e);
      }
    }
  }

  private void closeAndDetachSubscription() {
    Subscription current = currentSubscription.getAndSet(null);
    try {
      if (current != null) current.close();
    } catch (Exception justLog) {
      log.warn("Ignoring Exception while closing a subscription ({})", originalRequest, justLog);
    }
  }

  @VisibleForTesting
  void fail(Throwable exception) {
    log.error("Too many failures, giving up. ({})", originalRequest);
    close();
    currentSubscription.unblock();
    originalObserver.onError(exception);
    throw ExceptionHelper.toRuntime(exception);
  }

  @FunctionalInterface
  interface ThrowingBiConsumer<T, U> {
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
      log.info("Closing subscription due to onError triggered.  ({})", originalRequest, exception);
      closeAndDetachSubscription();

      if (resilience.shouldRetry(exception)) {
        log.info("Trying to resubscribe ({})", originalRequest);
        resilience.sleepForInterval();
        reConnect();
      } else {
        fail(exception);
      }
    }

    @Override
    public void onFactStreamInfo(@NonNull FactStreamInfo info) {
      originalObserver.onFactStreamInfo(info);
    }
  }

  @VisibleForTesting
  class SubscriptionHolder {
    // even though this is an atomicref, sync is necessary for wait/notify
    private final AtomicReference<InternalSubscription> currentSubscription =
        new AtomicReference<>();

    @NonNull
    public InternalSubscription getAndBlock() throws TimeoutException {
      return getAndBlock(0);
    }

    @NonNull
    public InternalSubscription getAndBlock(long maxPause) throws TimeoutException {
      long end = System.currentTimeMillis() + maxPause;
      synchronized (currentSubscription) {
        do {
          assertSubscriptionStateNotClosed();
          if (currentSubscription.get() == null) {
            try {
              long now = System.currentTimeMillis();
              long waitTime = maxPause == 0 ? 0 : end - now;
              if (maxPause != 0 && waitTime < 1)
                throw new TimeoutException("Timeout while acquiring subscription");

              currentSubscription.wait(waitTime);
            } catch (InterruptedException ignore) {
              // can be ignored
              Thread.currentThread().interrupt();
            }
          }
        } while (currentSubscription.get() == null);
        return currentSubscription.get();
      }
    }

    InternalSubscription getAndSet(@SuppressWarnings("SameParameterValue") InternalSubscription s) {
      synchronized (currentSubscription) {
        return currentSubscription.getAndSet(s);
      }
    }

    void set(InternalSubscription s) {
      synchronized (currentSubscription) {
        currentSubscription.set(s);
        currentSubscription.notifyAll();
      }
    }

    public InternalSubscription get() {
      synchronized (currentSubscription) {
        return currentSubscription.get();
      }
    }

    /** used to unblock getAndBlock calls */
    public void unblock() {
      synchronized (currentSubscription) {
        currentSubscription.notifyAll();
      }
    }
  }
}
