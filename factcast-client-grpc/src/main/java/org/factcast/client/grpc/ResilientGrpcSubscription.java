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
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.factcast.client.grpc.FactCastGrpcClientProperties.ResilienceConfiguration;
import org.factcast.core.Fact;
import org.factcast.core.FactStreamPosition;
import org.factcast.core.subscription.*;
import org.factcast.core.subscription.observer.FactObserver;
import org.factcast.core.util.ExceptionHelper;

@Slf4j
public class ResilientGrpcSubscription extends AbstractSubscription {
  private final GrpcFactStore store;
  private final SubscriptionRequestTO originalRequest;
  private final FactObserver originalObserver;
  private final FactObserver delegatingObserver;

  private final AtomicReference<FactStreamPosition> lastPosition = new AtomicReference<>();
  private final SubscriptionHolder currentSubscription = new SubscriptionHolder();

  @Getter(AccessLevel.PACKAGE)
  private final AtomicReference<Throwable> onErrorCause = new AtomicReference<>();

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
  public void internalClose() {
    closeAndDetachSubscription();
  }

  @VisibleForTesting
  ResilientGrpcSubscription delegate(
      ThrowingBiConsumer<Subscription, Long> consumer, long waitTimeInMillis)
      throws TimeoutException {
    long startTime = NowProvider.get();
    for (; ; ) {
      assertSubscriptionStateNotClosed();
      long maxPause = waitTimeInMillis - (NowProvider.get() - startTime);

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
      if ((NowProvider.get() - startTime) > waitTimeInMillis) {
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

  @SneakyThrows
  private void assertSubscriptionStateNotClosed() {
    if (isSubscriptionClosed()) {
      Throwable cause = onErrorCause.get();
      if (cause != null) {
        // Re-throwing exception if there is a known reason for the closed subscription to
        // consistently reflect underlying problems like a StatusRuntimeException (e.g.
        // "PERMISSION_DENIED"). Otherwise, sometimes underlying problems are hidden behind a
        // generic SubscriptionClosedException, see https://github.com/factcast/factcast/issues/2949
        throw cause;
      } else {
        throw new SubscriptionClosedException(
            "Subscription already closed  (" + originalRequest + ")");
      }
    }
  }

  private synchronized void connect() {
    log.debug("Connecting ({})", originalRequest);
    initializeAndConnect();
  }

  @VisibleForTesting
  synchronized void reConnect() {
    log.debug("Reconnecting ({})", originalRequest);
    initializeAndConnect();
  }

  private void initializeAndConnect() {
    try {
      store.initializeIfNecessary();
      doConnect();
    } catch (RuntimeException e) {
      fail(e);
    }
  }

  @VisibleForTesting
  protected void doConnect() {
    resilience.registerAttempt();
    SubscriptionRequestTO to = SubscriptionRequestTO.from(originalRequest);
    FactStreamPosition last = lastPosition.get();
    if (last != null) {
      to.startingAfter(last.factId());
    }

    if (currentSubscription.get() == null) {
      try {
        Subscription plainSubscription = store.internalSubscribe(to, delegatingObserver);
        currentSubscription.set(plainSubscription);
        onErrorCause.set(null);
      } catch (Exception e) {
        fail(e);
      }
    }
  }

  private void closeAndDetachSubscription() {
    Subscription current = currentSubscription.getAndSet(null);
    try {
      if (current != null) {
        current.close();
      }
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
      if (!isSubscriptionClosed()) {
        originalObserver.onNext(element);
        lastPosition.set(FactStreamPosition.from(element));
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
    public void onFastForward(@NonNull FactStreamPosition factIdToFfwdTo) {
      originalObserver.onFastForward(factIdToFfwdTo);
    }

    @Override
    public void onError(@NonNull Throwable exception) {
      log.info("Closing subscription due to onError triggered.  ({})", originalRequest, exception);
      onErrorCause.set(exception);
      closeAndDetachSubscription();
      // reset the store state, as the connection *might* be broken.
      store.reset();

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

    @Override
    public void flush() {
      originalObserver.flush();
    }
  }

  @VisibleForTesting
  class SubscriptionHolder {
    // even though this is an atomicref, sync is necessary for wait/notify
    private final AtomicReference<Subscription> currentSubscription = new AtomicReference<>();

    @NonNull
    public Subscription getAndBlock() throws TimeoutException {
      return getAndBlock(0);
    }

    @NonNull
    public Subscription getAndBlock(long maxPause) throws TimeoutException {
      long end = NowProvider.get() + maxPause;
      synchronized (currentSubscription) {
        do {
          assertSubscriptionStateNotClosed();
          if (currentSubscription.get() == null) {
            try {
              long now = NowProvider.get();
              long waitTime = maxPause == 0 ? 0 : end - now;
              if (maxPause != 0 && waitTime < 1) {
                throw new TimeoutException("Timeout while acquiring subscription");
              }

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

    Subscription getAndSet(@SuppressWarnings("SameParameterValue") Subscription s) {
      synchronized (currentSubscription) {
        return currentSubscription.getAndSet(s);
      }
    }

    void set(Subscription s) {
      synchronized (currentSubscription) {
        currentSubscription.set(s);
        currentSubscription.notifyAll();
      }
    }

    public Subscription get() {
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

  /**
   * Just a vehicle for tests. Static mocking anything from java.lang - like
   * System.currentTimeMillis() - is not possible with Mockito. It complains: "It is not possible to
   * mock static methods of java.lang.System to avoid interfering with class loading what leads to
   * infinite loops".
   *
   * <p>Making System.currentTimeMillis() predictable became desirable to phrase more deterministic
   * expectations in unit tests.
   */
  @UtilityClass
  @VisibleForTesting
  static class NowProvider {
    static long get() {
      return System.currentTimeMillis();
    }
  }
}
