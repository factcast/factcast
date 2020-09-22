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

import org.factcast.core.snap.Snapshot;
import org.factcast.core.snap.SnapshotCache;
import org.factcast.core.snap.SnapshotId;
import org.factcast.factus.projection.SnapshotProjection;
import org.factcast.factus.serializer.SnapshotSerializer;

import lombok.NonNull;
import lombok.val;

public class ProjectionSnapshotRepositoryImpl extends AbstractSnapshotRepository implements
        ProjectionSnapshotRepository {

    private static final UUID FAKE_UUID = new UUID(0, 0); // needed to maintain

    private SnapshotSerializerSupplier serializerSupplier;

    public ProjectionSnapshotRepositoryImpl(@NonNull SnapshotCache snapshotCache,
            @NonNull SnapshotSerializerSupplier serializerSupplier) {
        super(snapshotCache);
        this.serializerSupplier = serializerSupplier;
    }
    // the PK.

    @Override
    public Optional<Snapshot> findLatest(
            @NonNull Class<? extends SnapshotProjection> type) {
        SnapshotId snapshotId = new SnapshotId(createKeyForType(type, () -> serializerSupplier
                .retrieveSerializer(type)), FAKE_UUID);
        return snapshotCache.getSnapshot(snapshotId)
                .map(s -> new Snapshot(snapshotId, s.lastFact(), s.bytes(), s.compressed()));
    }

    @Override
    public CompletableFuture<Void> put(SnapshotProjection projection, UUID state) {
        // this is done before going async for exception escalation reasons:
        Class<? extends SnapshotProjection> type = projection.getClass();
        SnapshotSerializer ser = serializerSupplier.retrieveSerializer(type);

        return CompletableFuture.runAsync(() -> {
            val id = new SnapshotId(createKeyForType(type, () -> ser), FAKE_UUID);
            putBlocking(new Snapshot(id, state, ser.serialize(projection), ser
                    .includesCompression()));
        });
    }

}
