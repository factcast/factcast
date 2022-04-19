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

import com.google.common.annotations.VisibleForTesting;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.Fact;
import org.factcast.core.store.FactStore;
import org.factcast.core.subscription.observer.FactObserver;
import org.factcast.core.util.ExceptionHelper;

@Slf4j
public class ReconnectingFactSubscriptionWrapper implements Subscription {

  @NonNull
  private final AtomicReference<Subscription> currentSubscription = new AtomicReference<>();

  @NonNull private final FactStore store;

  @NonNull private final SubscriptionRequestTO originalRequest;

  @NonNull private final FactObserver originalObserver;

  @NonNull
  @Getter(AccessLevel.PACKAGE)
  private final FactObserver observer;

  private final AtomicReference<UUID> factIdSeen = new AtomicReference<>();

  private final AtomicBoolean closed = new AtomicBoolean(false);

  private static final int ALLOWED_TIME_BETWEEN_RECONNECTS = 3000;

  private static final int ALLOWED_NUMBER_OF_RECONNECTS_BEFORE_ESCALATION = 5;

  private final ExecutorService es =
      Executors.newCachedThreadPool(
          new ThreadFactory() {

            final AtomicLong threadCount = new AtomicLong(0);

            @Override
            public Thread newThread(@NonNull Runnable r) {
              Thread thread = new Thread(r);
              thread.setDaemon(true);
              thread.setName("factcast-recon-sub-wrapper-" + threadCount.incrementAndGet());
              return thread;
            }
          });

  @Override
  public void close() throws Exception {

    closed.set(true);
    Subscription cur = currentSubscription.get();
    if (cur != null) {
      cur.close();
    }
    currentSubscription.set(null);
  }

  @Override
  public Subscription awaitCatchup() throws SubscriptionCancelledException {

    for (; ; ) {
      assertSubscriptionStateNotClosed();
      Subscription cur = currentSubscription.get();
      if (cur != null) {

        cur.awaitCatchup();
        return this;

      } else {
        sleep(100);
      }
    }
  }

  private void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException ignore) {
    }
  }

  @Override
  public Subscription awaitCatchup(long waitTimeInMillis)
      throws SubscriptionCancelledException, TimeoutException {

    long startTime = System.currentTimeMillis();
    for (; ; ) {
      assertSubscriptionStateNotClosed();
      Subscription cur = currentSubscription.get();

      if (cur != null) {

        cur.awaitCatchup(waitTimeInMillis);
        return this;
      } else {
        sleep(100);
      }
      if ((System.currentTimeMillis() - startTime) > waitTimeInMillis) {
        throw new TimeoutException();
      }
    }
  }

  @Override
  public Subscription awaitComplete() throws SubscriptionCancelledException {

    for (; ; ) {
      assertSubscriptionStateNotClosed();
      Subscription cur = currentSubscription.get();

      if (cur != null) {

        cur.awaitComplete();
        return this;

      } else {
        sleep(100);
      }
    }
  }

  @Override
  public Subscription awaitComplete(long waitTimeInMillis)
      throws SubscriptionCancelledException, TimeoutException {

    long startTime = System.currentTimeMillis();
    for (; ; ) {
      assertSubscriptionStateNotClosed();
      Subscription cur = currentSubscription.get();

      if (cur != null) {

        cur.awaitComplete(waitTimeInMillis);
        return this;

        // escalate TimeoutException
      } else {
        sleep(100);
      }

      if ((System.currentTimeMillis() - startTime) > waitTimeInMillis) {
        throw new TimeoutException();
      }
    }
  }

  private void assertSubscriptionStateNotClosed() {
    if (closed.get()) {
      throw new SubscriptionCancelledException("Subscription already cancelled");
    }
  }

  public ReconnectingFactSubscriptionWrapper(
      @NonNull FactStore store, @NonNull SubscriptionRequestTO req, @NonNull FactObserver obs) {
    this.store = store;
    originalObserver = obs;
    originalRequest = req;

    observer =
        new FactObserver() {

          private final List<Long> reconnects = new LinkedList<>();

          @Override
          public void onNext(@NonNull Fact element) {
            if (!closed.get()) {
              originalObserver.onNext(element);
              factIdSeen.set(element.id());
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
            closeAndDetachSubscription();

            log.info("Closing subscription due to onError triggered.", exception);

            if (isServerException(exception) || reconnectedTooOften()) {

              // TODO log recorded exceptions

              // give up
              originalObserver.onError(exception);
              throw ExceptionHelper.toRuntime(exception);

            } else {

              log.debug("Pausing 100ms before reconnect");
              sleep(100);
              log.info("Trying to reconnect.");

              CompletableFuture.runAsync(
                  ReconnectingFactSubscriptionWrapper.this::initiateReconnect, es);
            }
          }

          private boolean reconnectedTooOften() {
            long now = System.currentTimeMillis();
            // remove all older reconnection attempts
            reconnects.removeIf(t -> now - t > ALLOWED_TIME_BETWEEN_RECONNECTS);
            reconnects.add(now);
            return reconnects.size() > ALLOWED_NUMBER_OF_RECONNECTS_BEFORE_ESCALATION;
          }

          @Override
          public void onFactStreamInfo(FactStreamInfo info) {
            originalObserver.onFactStreamInfo(info);
          }
        };
    initiateReconnect();
  }

  @VisibleForTesting
  boolean isServerException(@NonNull Throwable exception) {

    if (exception instanceof StaleSubscriptionDetectedException) {
      // assume connection problem
      return false;
    }

    return exception.getClass().getName().startsWith("org.factcast");
  }

  private synchronized void initiateReconnect() {
    SubscriptionRequestTO to = SubscriptionRequestTO.forFacts(originalRequest);
    UUID last = factIdSeen.get();
    if (last != null) {
      to.startingAfter(last);
    }

    for (; ; ) {
      try {
        if (currentSubscription.get() == null) {
          // might throw exceptions
          Subscription subscribe = store.subscribe(to, observer);
          currentSubscription.compareAndSet(null, subscribe);
        }
        return;
      } catch (Exception ignore) {
        sleep(500);
      }
    }
  }

  private void closeAndDetachSubscription() {
    Subscription current = currentSubscription.getAndSet(null);
    try {
      current.close();
    } catch (Exception justLog) {
      log.warn("Ignoring Exception while closing a subscription:", justLog);
    }
  }
}
