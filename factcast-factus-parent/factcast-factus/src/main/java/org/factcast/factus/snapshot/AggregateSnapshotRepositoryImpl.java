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

import org.factcast.core.snap.SnapshotId;
import org.factcast.core.snap.SnapshotRepository;
import org.factcast.factus.projection.Aggregate;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.val;

@RequiredArgsConstructor
public class AggregateSnapshotRepositoryImpl extends BaseSnapshotRepository implements
        AggregateSnapshotRepository {

    private final SnapshotRepository snap;

    @Override
    public <A extends Aggregate> Optional<Snapshot> findLatest(
            @NonNull Class<A> type,
            @NonNull UUID aggregateId) {
        SnapshotId snapshotId = new SnapshotId(createKeyForType(type), aggregateId);
        return snap.getSnapshot(snapshotId)
                .map(s -> new Snapshot(type, s.lastFact(), s.bytes()));

    }

    @Override
    public <A extends Aggregate> void putBlocking(
            @NonNull UUID aggregateId,
            @NonNull Snapshot snapshot) {
        val snapId = new SnapshotId(createKeyForType(snapshot.type()), aggregateId);
        snap.setSnapshot(snapId, snapshot.factId(), snapshot.bytes());
    }

}
