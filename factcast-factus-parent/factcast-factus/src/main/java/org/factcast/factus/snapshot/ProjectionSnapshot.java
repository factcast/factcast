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

import org.factcast.factus.projection.SnapshotProjection;
import org.factcast.factus.serializer.DefaultSnapshotSerializer;
import org.factcast.factus.serializer.SnapshotSerializer;

import lombok.Data;
import lombok.NonNull;

@Data
public class ProjectionSnapshot<P extends SnapshotProjection> {
    private static SnapshotSerializer defaultSerializer = new DefaultSnapshotSerializer();

    Class<P> type;

    @NonNull
    UUID factId;

    @NonNull
    byte[] bytes;

    public ProjectionSnapshot(Class<P> aggregateClass, UUID lastFactId, P aggregate) {
        this(aggregateClass, lastFactId, serialize(aggregate));
    }

    // bug in lombok plugin for intellij, please do not delete, even if it looks
    // redundant.
    public ProjectionSnapshot(Class<P> aggregateClass, UUID lastFactId, byte[] bytes) {
        this.type = aggregateClass;
        this.bytes = bytes;
        this.factId = lastFactId;
    }

    public static <T extends SnapshotProjection> byte[] serialize(T aggregate) {
        // TODO configure by annotations
        return defaultSerializer.serialize(aggregate);
    }

    public static <T extends SnapshotProjection> T deserialize(
            ProjectionSnapshot<T> snap) {
        // TODO configure by annotations
        return defaultSerializer.deserialize(snap.type(), snap.bytes());
    }
}
