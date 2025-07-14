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
package org.factcast.factus;

import com.google.common.base.Throwables;
import com.google.common.cache.*;
import com.google.common.util.concurrent.UncheckedExecutionException;
import java.io.Closeable;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import lombok.NonNull;
import org.factcast.core.*;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.store.FactStore;
import org.factcast.core.subscription.Subscription;
import org.factcast.factus.batch.PublishBatch;
import org.factcast.factus.event.EventObject;
import org.factcast.factus.lock.*;
import org.factcast.factus.projection.*;
import org.slf4j.*;

/**
 * Factus is a high-level API that should make building EDA with FactCast from java more convenient.
 */
@SuppressWarnings({"unused", "UnusedReturnValue"})
public interface Factus extends SimplePublisher, ProjectionAccessor, Closeable {

  IntToLongFunction DEFAULT_RETRY_BACKOFF = i -> 100;
  Logger LOGGER = LoggerFactory.getLogger(Factus.class);
  Cache<UUID, Long> serialCache = CacheBuilder.newBuilder().maximumSize(1000).build();

  //// Publishing

  /** publishes a single event immediately */
  @Override
  default void publish(@NonNull EventObject eventPojo) {
    publish(eventPojo, f -> null);
  }

  /** publishes a list of events immediately in an atomic manner (all or none) */
  @Override
  default void publish(@NonNull List<EventObject> eventPojos) {
    publish(eventPojos, f -> null);
  }

  /**
   * publishes a single event immediately and transforms the resulting facts to a return type with
   * the given resultFn
   */
  <T> T publish(@NonNull EventObject e, @NonNull Function<Fact, T> resultFn);

  /**
   * publishes a list of events immediately in an atomic manner (all or none) and transforms the
   * resulting facts to a return type with the given resultFn
   */
  <T> T publish(@NonNull List<EventObject> e, @NonNull Function<List<Fact>, T> resultFn);

  /**
   * creates a batch that can be passed around and added to. It is different from publishing a list
   * of events in the respect that a batch can be markedAborted.
   */
  @NonNull
  PublishBatch batch();

  // subscribed projections:

  /**
   * creates a permanent, async subscription to the projection. The subscription will be remembered
   * for autoclose, when Factus is closed. (Probably more of a shutdown hook) This method sets a
   * default wait time between retries of 5 minutes.
   *
   * <p>Note that this method will block forever, if this node fails to acquire a writer token.
   */
  <P extends SubscribedProjection> Subscription subscribeAndBlock(@NonNull P subscribedProjection);

  /**
   * creates a permanent, async subscription to the projection. The subscription will be remembered
   * for autoclose, when Factus is closed. (Probably more of a shutdown hook)
   *
   * <p>Note that this method will block forever, if this node fails to acquire a writer token.
   */
  <P extends SubscribedProjection> Subscription subscribeAndBlock(
      @NonNull P subscribedProjection, @NonNull Duration retryWaitTime);

  /**
   * Method returns immediately, but you won't know if subscription was sucessful (kind of "keep
   * trying to subscribe" and forget)
   */
  default <P extends SubscribedProjection> void subscribe(@NonNull P subscribedProjection) {
    CompletableFuture.runAsync(
        () -> {
          try {
            subscribeAndBlock(subscribedProjection);
          } catch (FactusClosedException fce) {
            LOGGER.info(
                "Aborting subscription {}: {}", subscribedProjection.getClass(), fce.getMessage());
          } catch (Exception e) {
            LOGGER.error("Error subscribing to {}", subscribedProjection.getClass(), e);
          }
        });
  }

  // Locking

  /** optimistically 'locks' on an aggregate. */
  <A extends Aggregate> Locked<A> withLockOn(@NonNull Class<A> aggregateClass, UUID id);

  /** optimistically 'locks' on an aggregate. shortcut to lock(Class,UUID) */
  @SuppressWarnings("unchecked")
  default <S extends SnapshotProjection> Locked<S> withLockOn(@NonNull S snapshotProjection) {
    if (snapshotProjection instanceof Aggregate) {
      Aggregate aggregate = (Aggregate) snapshotProjection;
      return (Locked<S>) withLockOn(aggregate.getClass(), AggregateUtil.aggregateId(aggregate));
    } else {
      return (Locked<S>) withLockOn(snapshotProjection.getClass());
    }
  }

  /** optimistically 'locks' on a SnapshotProjection */
  <P extends SnapshotProjection> Locked<P> withLockOn(@NonNull Class<P> snapshotClass);

  /** optimistically 'locks' on a ManagedProjection */
  <M extends ManagedProjection> Locked<M> withLockOn(@NonNull M managed);

  // conversion
  Fact toFact(@NonNull EventObject e);

  LockedOnSpecs withLockOn(@NonNull FactSpec spec, @NonNull FactSpec... additional);

  LockedOnSpecs withLockOn(@NonNull List<FactSpec> specs);

  OptionalLong serialOf(@NonNull UUID factId);

  /**
   * Blocks until the subscribedProjection has consumed the fact with the given factId or one that
   * was published after it. The method will retry to check the state of the projection, until
   * timeout. The retry interval can be customized by providing a retryBackoffMillis function, that
   * takes the number of retries as input and returns the number of milliseconds to wait before the
   * next retry.
   *
   * @throws TimeoutException if the fact with the given id or a subsequent one is not consumed
   *     within the timeout.
   * @throws IllegalArgumentException if the fact with the given id is not published yet or can't be
   *     found.
   */
  default <P extends SubscribedProjection> void waitFor(
      @NonNull P subscribedProjection,
      @NonNull UUID factId,
      @NonNull Duration timeout,
      @NonNull IntToLongFunction retryBackoffMillis)
      throws TimeoutException, IllegalArgumentException {
    try {
      long start = System.currentTimeMillis();
      long serial =
          serialCache.get(
              factId,
              () ->
                  this.serialOf(factId)
                      .orElseThrow(
                          () ->
                              new IllegalArgumentException(
                                  String.format(
                                      "Fact with id %s not found. Make sure to publish before waiting for it.",
                                      factId))));
      FactStreamPosition currentFsp = subscribedProjection.factStreamPosition();
      // until timeout is met or the factStreamPosition is greater than the serial
      for (int i = 1; currentFsp == null || currentFsp.serial() < serial; i++) {
        if (System.currentTimeMillis() - start > timeout.toMillis()) {
          throw new TimeoutException(
              String.format("Timeout waiting for fact %s to be consumed.", factId));
        }
        Thread.sleep(retryBackoffMillis.applyAsLong(i));
        currentFsp = subscribedProjection.factStreamPosition();
      }
    } catch (UncheckedExecutionException | ExecutionException e) {
      // unwrap ExecutionExceptions from the cache
      Throwables.throwIfUnchecked(e.getCause());
      throw new IllegalStateException(e);
    } catch (InterruptedException e1) {
      LOGGER.info("Interrupted while waiting for fact {} to be consumed.", factId);
      Thread.currentThread().interrupt();
    }
  }

  /**
   * Blocks until the subscribedProjection has consumed the fact with the given factId or a
   * subsequent one. The method will retry every 100ms to check the state of the projection, until
   * timeout.
   *
   * @throws TimeoutException if the fact with the given id or a subsequent one is not consumed
   *     within the timeout.
   * @throws IllegalArgumentException if the fact with the given id is not published yet or can't be
   *     found.
   */
  default <P extends SubscribedProjection> void waitFor(
      @NonNull P subscribedProjection, @NonNull UUID factId, @NonNull Duration timeout)
      throws TimeoutException, IllegalArgumentException {
    waitFor(subscribedProjection, factId, timeout, DEFAULT_RETRY_BACKOFF);
  }

  /**
   * Internal API: subject to change - use at your own risk
   *
   * @since 0.7.10
   */
  @NonNull
  FactStore store();

  /**
   * @return Current time as Instant from the factstore.
   *     <p>Can be used to synchronize across clients (e.g. to decide if it is too late to publish a
   *     certain event)
   * @since 0.10.0
   */
  @NonNull
  default Instant currentTime() {
    return Instant.ofEpochMilli(store().currentTime());
  }
}
