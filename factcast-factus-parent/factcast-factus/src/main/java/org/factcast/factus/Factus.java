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
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import org.factcast.core.Fact;
import org.factcast.factus.batch.PublishBatch;
import org.factcast.factus.lock.Locked;
import org.factcast.factus.projection.*;

import lombok.NonNull;
import lombok.SneakyThrows;

public interface Factus extends Closeable {

    default void publish(@NonNull EventPojo e) {
        publish(e, f -> null);
    }

    default void publish(@NonNull List<EventPojo> e) {
        publish(e, f -> null);
    }

    <T> T publish(@NonNull EventPojo e, @NonNull Function<Fact, T> resultFn);

    <T> T publish(@NonNull List<EventPojo> e, @NonNull Function<List<Fact>, T> resultFn);

    // TODO find a good place for:
    public final Duration FOREVER = Duration.ofDays(1);

    // publishing:
    PublishBatch batch();

    // snapshot projections:

    <P extends SnapshotProjection> P fetch(@NonNull Class<P> projectionClass);

    <A extends Aggregate> Optional<A> fetch(
            @NonNull Class<A> aggregateClass,
            @NonNull UUID aggregateId);

    // managed projections:

    @SneakyThrows
    default <P extends ManagedProjection> void update(@NonNull P managedProjection) {
        update(managedProjection, Factus.FOREVER);
    }

    <P extends ManagedProjection> void update(
            @NonNull P managedProjection,
            @NonNull Duration maxWaitTime)
            throws TimeoutException;

    // subscribed projections:
    <P extends SubscribedProjection> void subscribe(@NonNull P subscribedProjection);

    // Locking
    // TODO needed? Locked lockAggregateById(UUID first, UUID... others);

    // conversion
    Fact toFact(@NonNull EventPojo e);

    <A extends Aggregate> Locked<A> lock(@NonNull Class<A> aggregateClass, UUID id);

    <P extends SnapshotProjection> Locked<P> lock(@NonNull Class<P> snapshotClass);

    <M extends ManagedProjection> Locked<M> lock(@NonNull M managed);

}
