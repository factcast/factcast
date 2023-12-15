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

import java.io.Closeable;
import java.time.Duration;
import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import lombok.NonNull;
import org.factcast.core.Fact;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.subscription.Subscription;
import org.factcast.factus.batch.PublishBatch;
import org.factcast.factus.event.EventObject;
import org.factcast.factus.lock.Locked;
import org.factcast.factus.lock.LockedOnSpecs;
import org.factcast.factus.projection.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factus is a high-level API that should make building EDA with FactCast from java more convenient.
 */
@SuppressWarnings({"unused", "UnusedReturnValue"})
public interface Factus extends SimplePublisher, ProjectionAccessor, Closeable {

  Logger LOGGER = LoggerFactory.getLogger(Factus.class);
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
   * Method returns immediately, but you wont know if subscription was sucessful (kind of "keep
   * trying to subscribe" and forget)
   */
  default <P extends SubscribedProjection> void subscribe(@NonNull P subscribedProjection) {
    CompletableFuture.runAsync(
        () -> {
          try {
            subscribeAndBlock(subscribedProjection);
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
}
