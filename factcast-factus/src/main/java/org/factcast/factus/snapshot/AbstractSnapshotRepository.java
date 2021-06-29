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

import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.snap.Snapshot;
import org.factcast.core.snap.SnapshotCache;
import org.factcast.factus.metrics.FactusMetrics;
import org.factcast.factus.metrics.GaugedEvent;
import org.factcast.factus.metrics.TagKeys;
import org.factcast.factus.projection.ScopedName;
import org.factcast.factus.projection.SnapshotProjection;
import org.factcast.factus.serializer.SnapshotSerializer;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

@RequiredArgsConstructor
@Slf4j
abstract class AbstractSnapshotRepository {
  protected final SnapshotCache snapshotCache;
  private final FactusMetrics factusMetrics;

  protected void putBlocking(@NonNull Snapshot snapshot) {
    snapshotCache.setSnapshot(snapshot);
  }

  protected String createKeyForType(
      @NonNull Class<? extends SnapshotProjection> type,
      @NonNull Supplier<SnapshotSerializer> serializerSupplier) {
    return createKeyForType(type, serializerSupplier, null);
  }

  @SuppressWarnings("SameParameterValue")
  protected String createKeyForType(
      @NonNull Class<? extends SnapshotProjection> type,
      @NonNull Supplier<SnapshotSerializer> serializerSupplier,
      UUID optionalUUID) {

    ScopedName classLevelKey =
        ScopedName.forClass(type).with(getId()).with(serializerId(serializerSupplier));

    if (optionalUUID != null) {
      classLevelKey = classLevelKey.with(optionalUUID.toString());
    }

    return classLevelKey.toString();
  }

  private String serializerId(Supplier<SnapshotSerializer> serializerSupplier) {
    return serializerSupplier.get().getId();
  }

  protected abstract String getId();

  @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
  protected void recordSnapshotSize(
      Optional<Snapshot> ret, Class<? extends SnapshotProjection> projectionClass) {
    ret.ifPresent(
        s ->
            factusMetrics.record(
                GaugedEvent.FETCH_SIZE,
                Tags.of(Tag.of(TagKeys.CLASS, projectionClass.getName())),
                s.bytes().length));
  }
}
