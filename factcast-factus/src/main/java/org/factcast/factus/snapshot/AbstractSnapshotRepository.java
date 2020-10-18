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

import com.google.common.annotations.VisibleForTesting;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.snap.Snapshot;
import org.factcast.core.snap.SnapshotCache;
import org.factcast.factus.projection.SnapshotProjection;
import org.factcast.factus.serializer.ProjectionMetaData;
import org.factcast.factus.serializer.SnapshotSerializer;

@RequiredArgsConstructor
@Slf4j
abstract class AbstractSnapshotRepository {
  protected static final String KEY_DELIMITER = ":";

  protected final SnapshotCache snapshotCache;

  private final Map<Class<? extends SnapshotProjection>, String> typeSerializerAndSerialUIdCache =
      new ConcurrentHashMap<>();

  protected void putBlocking(@NonNull Snapshot snapshot) {
    snapshotCache.setSnapshot(snapshot);
  }

  @VisibleForTesting
  protected String createKeyForType(
      @NonNull Class<? extends SnapshotProjection> type,
      @NonNull Supplier<SnapshotSerializer> serializerSupplier) {
    return createKeyForType(type, serializerSupplier, null);
  }

  @VisibleForTesting
  protected String createKeyForType(
      @NonNull Class<? extends SnapshotProjection> type,
      @NonNull Supplier<SnapshotSerializer> serializerSupplier,
      UUID optionalUUID) {

    String classLevelKey =
        keyPrefix()
            + type.getCanonicalName()
            + KEY_DELIMITER
            + serializerAndSerialUId(type, serializerSupplier);

    if (optionalUUID == null) {
      return classLevelKey;
    } else {
      return classLevelKey + KEY_DELIMITER + optionalUUID.toString();
    }
  }

  private String serializerAndSerialUId(
      Class<? extends SnapshotProjection> type, Supplier<SnapshotSerializer> serializerSupplier) {

    return typeSerializerAndSerialUIdCache.computeIfAbsent(
        type,
        t -> {
          SnapshotSerializer serializer = serializerSupplier.get();
          Long serialVersionUid = getSerialVersionUid(type, serializerSupplier);
          if (serialVersionUid == null) {
            log.error(
                "Cannot determine serial for class "
                    + t.getCanonicalName()
                    + ". Falling back to currentTimeMillis to avoid deserialization errors. However this *WILL* flood your SnapshotCache with useless Snapshots, so please provide a serial for this class.");
            serialVersionUid = System.currentTimeMillis();
          }

          return serializer.getClass().getCanonicalName() + KEY_DELIMITER + serialVersionUid;
        });
  }

  private final Map<Class<? extends SnapshotProjection>, Long> serials = new HashMap<>();

  @VisibleForTesting
  protected Long getSerialVersionUid(
      Class<? extends SnapshotProjection> type, Supplier<SnapshotSerializer> serializerSupplier) {
    SnapshotSerializer serializer = serializerSupplier.get();
    return serials.computeIfAbsent(
        type,
        t -> {

          // 1st: @ProjectionMetaData
          ProjectionMetaData annotation = t.getAnnotation(ProjectionMetaData.class);
          if (annotation != null) {
            return annotation.hash();
          }

          // 2nd: static serialVersionUID
          try {
            Field field = t.getDeclaredField("serialVersionUID");
            field.setAccessible(true);
            return field.getLong(null);
          } catch (NoSuchFieldException | IllegalAccessException e) {
          }

          // fallback: calculated hash
          return serializer.calculateProjectionSerial(t);
        });
  }

  protected String keyPrefix() {
    return getClass().getCanonicalName() + KEY_DELIMITER;
  }
}
