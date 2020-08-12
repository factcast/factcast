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
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import org.factcast.core.Fact;
import org.factcast.core.subscription.Subscription;
import org.factcast.factus.batch.PublishBatch;
import org.factcast.factus.lock.Locked;
import org.factcast.factus.projection.Aggregate;
import org.factcast.factus.projection.ManagedProjection;
import org.factcast.factus.projection.SnapshotProjection;
import org.factcast.factus.projection.SubscribedProjection;

import lombok.NonNull;

/**
 * Factus is a high-level API that should make building EDA with FactCast from
 * java more convenient.
 */
public interface Factus extends SimplePublisher, ProjectionAccessor, Closeable {

    //// Publishing

    /**
     * publishes a single event immediately
     */
    default void publish(@NonNull EventPojo eventPojo) {
        publish(eventPojo, f -> null);
    }

    /**
     * publishes a list of events immediately in an atomic manner (all or none)
     */
    default void publish(@NonNull List<EventPojo> eventPojos) {
        publish(eventPojos, f -> null);
    }

    /**
     * publishes a single event immediately and transforms the resulting facts
     * to a return type with the given resultFn
     */
    <T> T publish(@NonNull EventPojo e, @NonNull Function<Fact, T> resultFn);

    /**
     * publishes a list of events immediately in an atomic manner (all or none)
     * and transforms the resulting facts to a return type with the given
     * resultFn
     */
    <T> T publish(@NonNull List<EventPojo> e, @NonNull Function<List<Fact>, T> resultFn);

    /**
     * creates a batch that can be passed around and added to. It is different
     * from publishing a list of events in the respect that a batch can be
     * markedAborted.
     */
    @NonNull
    PublishBatch batch();

    // subscribed projections:

    /**
     * creates a permanent, async subscription to the projection. The
     * subscription will be remembered for autoclose, when Factus is closed.
     * (Probably more of a shutdown hook)
     *
     * @param subscribedProjection
     * @param <P>
     */
    <P extends SubscribedProjection> Subscription subscribe(@NonNull P subscribedProjection);

    // Locking
    // TODO needed? Locked lockAggregateById(UUID first, UUID... others);

    /**
     * optimistically 'locks' on an aggregate.
     */
    <A extends Aggregate> Locked<A> lock(@NonNull Class<A> aggregateClass, UUID id);

    /**
     * optimistically 'locks' on a SnapshotProjection
     */

    <P extends SnapshotProjection> Locked<P> lock(@NonNull Class<P> snapshotClass);

    /**
     * optimistically 'locks' on a ManagedProjection
     */
    <M extends ManagedProjection> Locked<M> lock(@NonNull M managed);

    // conversion
    Fact toFact(@NonNull EventPojo e);

}
