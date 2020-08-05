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
package org.factcast.factus.snapshot;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.factcast.factus.projection.Aggregate;

import lombok.NonNull;

public interface AggregateSnapshotRepository {
    <A extends Aggregate> Optional<AggregateSnapshot<A>> findLatest(@NonNull Class<A> type,
            @NonNull UUID aggregateId);

    <A extends Aggregate> void putBlocking(@NonNull UUID aggregateId,
            @NonNull AggregateSnapshot<A> snapshot);

    <A extends Aggregate> CompletableFuture<Void> put(@NonNull UUID aggregateId,
            @NonNull AggregateSnapshot<A> snapshot);
}
