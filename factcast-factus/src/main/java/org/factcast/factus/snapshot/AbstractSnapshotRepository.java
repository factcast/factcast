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
import java.util.Optional;
import java.util.UUID;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.factcast.factus.metrics.FactusMetrics;
import org.factcast.factus.metrics.GaugedEvent;
import org.factcast.factus.metrics.TagKeys;
import org.factcast.factus.projection.*;
import org.factcast.factus.serializer.SnapshotSerializer;

@RequiredArgsConstructor
@Slf4j
abstract class AbstractSnapshotRepository {
  @NonNull private final SnapshotCache snapshotCache;
  @NonNull private final FactusMetrics factusMetrics;
  @NonNull private final SnapshotSerializerSelector selector;

  @NonNull
  protected Optional<SnapshotData> find(@NonNull SnapshotIdentifier id) {
    @NonNull Optional<SnapshotData> ret = snapshotCache.find(id);
    ret.ifPresent(s -> recordSnapshotSize(s, id.projectionClass()));
    return ret;
  }

  protected void store(@NonNull SnapshotIdentifier id, @NonNull SnapshotData snapshot) {
    snapshotCache.store(id, snapshot);
  }

  protected void remove(@NonNull SnapshotIdentifier id) {
    snapshotCache.remove(id);
  }

  // encapsulated for a reason
  @NonNull
  private SnapshotSerializer seralizerFor(@NonNull Class<? extends SnapshotProjection> type) {
    return selector.selectSeralizerFor(type);
  }

  protected final <T extends SnapshotProjection> T deserialize(
      @NonNull byte[] bytes, @NonNull Class<T> type) {
    try {
      T instance = seralizerFor(type).deserialize(type, bytes);
      instance.onAfterRestore();
      return instance;
    } catch (Exception e) {
      log.warn("Failed to deserialize snapshot of type {}", type, e);
      return null;
    }
  }

  protected final <T extends SnapshotProjection> SnapshotData serialize(
      @NonNull T instance, @NonNull UUID state) {
    instance.onBeforeSnapshot();
    SnapshotSerializer ser = seralizerFor(instance.getClass());
    return new SnapshotData(ser.serialize(instance), ser.id(), state);
  }

  private void recordSnapshotSize(
      @NonNull SnapshotData ret, @NonNull Class<? extends SnapshotProjection> projectionClass) {
    factusMetrics.record(
        GaugedEvent.FETCH_SIZE,
        Tags.of(Tag.of(TagKeys.CLASS, projectionClass.getName())),
        ret.serializedProjection().length);
  }

  protected <T extends SnapshotProjection> Optional<ProjectionAndState<T>> findAndDeserialize(
      Class<T> type, SnapshotIdentifier id) {
    Optional<SnapshotData> snapshotData = find(id);
    if (snapshotData.isPresent()) {
      SnapshotData sd = snapshotData.get();
      return Optional.ofNullable(deserialize(sd.serializedProjection(), type))
          .map(i -> ProjectionAndState.of(i, sd.lastFactId()));
    } else {
      return Optional.empty();
    }
  }
}
