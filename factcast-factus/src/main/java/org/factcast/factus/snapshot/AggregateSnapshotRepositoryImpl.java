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
import lombok.NonNull;
import lombok.val;
import org.factcast.core.snap.Snapshot;
import org.factcast.core.snap.SnapshotCache;
import org.factcast.core.snap.SnapshotId;
import org.factcast.factus.projection.Aggregate;
import org.factcast.factus.projection.AggregateUtil;
import org.factcast.factus.serializer.SnapshotSerializer;

public class AggregateSnapshotRepositoryImpl extends AbstractSnapshotRepository
    implements AggregateSnapshotRepository {

  private SnapshotSerializerSupplier serializerSupplier;

  public AggregateSnapshotRepositoryImpl(
      SnapshotCache snapshotCache, SnapshotSerializerSupplier serializerSupplier) {
    super(snapshotCache);
    this.serializerSupplier = serializerSupplier;
  }

  @Override
  public Optional<Snapshot> findLatest(
      @NonNull Class<? extends Aggregate> type, @NonNull UUID aggregateId) {

    SnapshotId snapshotId =
        new SnapshotId(
            createKeyForType(type, () -> serializerSupplier.retrieveSerializer(type)), aggregateId);

    return snapshotCache.getSnapshot(snapshotId);
  }

  @Override
  public CompletableFuture<Void> put(Aggregate aggregate, UUID state) {
    // this is done before going async for exception escalation reasons:
    Class<? extends Aggregate> type = aggregate.getClass();
    SnapshotSerializer ser = serializerSupplier.retrieveSerializer(type);

    return CompletableFuture.runAsync(
        () -> {
          val id =
              new SnapshotId(
                  createKeyForType(type, () -> ser), AggregateUtil.aggregateId(aggregate));
          putBlocking(new Snapshot(id, state, ser.serialize(aggregate), ser.includesCompression()));
        });
  }
}
