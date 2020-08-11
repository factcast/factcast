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
package org.factcast.factus.lock;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import org.factcast.core.Fact;
import org.factcast.factus.EventPojo;
import org.factcast.factus.Factus;
import org.factcast.factus.projection.Aggregate;
import org.factcast.factus.projection.ManagedProjection;
import org.factcast.factus.projection.SnapshotProjection;

import lombok.NonNull;
import lombok.SneakyThrows;

/**
 * Contains all operations that are available during locked execution
 */
public interface RetryableTransaction {

    void publish(@NonNull EventPojo e);

    void publish(@NonNull Fact e);

    // snapshot projections:

    <P extends SnapshotProjection> P fetch(@NonNull Class<P> projectionClass);

    <A extends Aggregate> Optional<A> fetch(
            @NonNull Class<A> aggregateClass,
            @NonNull UUID aggregateId);

    // managed projections:
    // TODO should we allow this? -> isolation

    @SneakyThrows
    default <P extends ManagedProjection> void update(@NonNull P managedProjection) {
        update(managedProjection, Factus.FOREVER);
    }

    <P extends ManagedProjection> void update(
            @NonNull P managedProjection,
            @NonNull Duration maxWaitTime)
            throws TimeoutException;

    default void abort(@NonNull String msg) {
        abort(new LockedOperationAbortedException(msg));
    }

    default void abort(@NonNull LockedOperationAbortedException cause) {
        throw cause;
    }

}
