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

import java.util.UUID;

import org.factcast.factus.projection.Aggregate;
import org.factcast.factus.serializer.DefaultSnapshotSerializer;
import org.factcast.factus.serializer.SnapshotSerializer;

import lombok.NonNull;
import lombok.Value;

@Value
public class AggregateSnapshot<A extends Aggregate> {
    private static SnapshotSerializer defaultSerializer = new DefaultSnapshotSerializer();

    Class<A> type;

    @NonNull
    UUID factId;

    @NonNull
    byte[] serializedAggregate;

    public AggregateSnapshot(Class<A> aggregateClass, UUID lastFactId, A aggregate) {
        this(aggregateClass, lastFactId, serialize(aggregate));
    }

    // bug in lombok plugin for intellij, please do not delete, even if it looks
    // redundant.
    public AggregateSnapshot(Class<A> aggregateClass, UUID lastFactId, byte[] bytes) {
        this.type = aggregateClass;
        this.factId = lastFactId;
        this.serializedAggregate = bytes;
    }

    public static <AGGREGATE extends Aggregate> byte[] serialize(AGGREGATE aggregate) {
        // TODO configure by annotations
        return defaultSerializer.serialize(aggregate);
    }

    public static <AGGREGATE extends Aggregate> AGGREGATE deserialize(
            AggregateSnapshot<AGGREGATE> aAggregateSnapshot) {
        // TODO configure by annotations
        return defaultSerializer.deserialize(aAggregateSnapshot.type(), aAggregateSnapshot
                .serializedAggregate());
    }
}
