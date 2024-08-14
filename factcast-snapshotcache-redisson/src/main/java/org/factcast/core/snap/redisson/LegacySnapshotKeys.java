/*
 * Copyright © 2017-2024 factcast.org
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
package org.factcast.core.snap.redisson;

import java.util.UUID;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import org.factcast.factus.projection.ScopedName;
import org.factcast.factus.projection.SnapshotProjection;
import org.factcast.factus.serializer.SnapshotSerializer;

/**
 * used for downward compatibility within redis impl of snapshotcache. Will be removed eventually.
 */
@Deprecated
public class LegacySnapshotKeys {
  @Getter
  @AllArgsConstructor
  public enum RepoType {
    // ids used before 0.8
    SNAPSHOT("ProjectionSnapshotRepositoryImpl"),
    AGGREGATE("AggregateSnapshotRepositoryImpl");
    final String id;
  }

  @NonNull
  public static String createKeyForType(
      @NonNull RepoType repoType,
      @NonNull Class<? extends SnapshotProjection> type,
      @NonNull Supplier<SnapshotSerializer> serializerSupplier) {
    return createKeyForType(repoType, type, serializerSupplier, null);
  }

  @NonNull
  public static String createKeyForType(
      @NonNull RepoType repoType,
      @NonNull Class<? extends SnapshotProjection> type,
      @NonNull Supplier<SnapshotSerializer> serializerSupplier,
      @Nullable UUID optionalUUID) {

    ScopedName classLevelKey =
        ScopedName.fromProjectionMetaData(type)
            .with(repoType.id())
            .with(serializerId(serializerSupplier));

    if (optionalUUID != null) {
      classLevelKey = classLevelKey.with(optionalUUID.toString());
    }

    return classLevelKey.asString();
  }

  @NonNull
  private static String serializerId(@NonNull Supplier<SnapshotSerializer> serializerSupplier) {
    return serializerSupplier.get().id().name();
  }
}
