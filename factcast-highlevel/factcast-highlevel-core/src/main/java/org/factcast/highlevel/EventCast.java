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
package org.factcast.highlevel;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import org.factcast.core.Fact;
import org.factcast.highlevel.batch.PublishBatch;
import org.factcast.highlevel.projection.Aggregate;
import org.factcast.highlevel.projection.ManagedProjection;
import org.factcast.highlevel.projection.SnapshotProjection;

import lombok.NonNull;
import lombok.SneakyThrows;

public interface EventCast {
    PublishBatch batch();

    void publish(@NonNull EventPojo e);

    void publish(@NonNull List<EventPojo> e);

    <T> T publish(@NonNull EventPojo e, @NonNull Function<Fact, T> resultFn);

    <T> T publish(@NonNull List<EventPojo> e, @NonNull Function<List<Fact>, T> resultFn);

    // snapshot projections
    <P extends SnapshotProjection> P fetch(@NonNull Class<P> projectionClass);

    <A extends Aggregate> Optional<A> fetch(@NonNull Class<A> aggregateClass,
            @NonNull UUID aggregateId);
    // managed projections

    @SneakyThrows
    <P extends ManagedProjection> void update(@NonNull P managedProjection);

    @SneakyThrows
    <P extends ManagedProjection> void update(@NonNull P managedProjection,
            @NonNull Duration maxWaitTime)
            throws TimeoutException;

}
