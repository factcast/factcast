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

import jakarta.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import org.factcast.factus.projection.Aggregate;
import org.factcast.factus.projection.ScopedName;
import org.factcast.factus.projection.SnapshotProjection;
import org.factcast.factus.serializer.SnapshotSerializerId;

/**
 * used for downward compatibility within redis impl of snapshotcache. Will be removed eventually.
 *
 * @deprecated
 */
@Deprecated
public class LegacySnapshotKeys {
  private static final UUID DEFAULT_UUID = new UUID(0, 0);

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
      @NonNull Class<? extends SnapshotProjection> type, @NonNull SnapshotSerializerId serId) {
    return createKeyForType(type, serId, null);
  }

  @NonNull
  public static String createKeyForType(
      @NonNull Class<? extends SnapshotProjection> type,
      @NonNull SnapshotSerializerId serId,
      @Nullable UUID aggregateId) {

    // better safe than sorry
    if (Aggregate.class.isAssignableFrom(type)) {
      return createKeyForType(RepoType.AGGREGATE, type, serId, aggregateId);
    } else {
      return createKeyForType(RepoType.SNAPSHOT, type, serId, null);
    }
  }

  private static @NonNull String createKeyForType(
      @NonNull RepoType repoType,
      @NonNull Class<? extends SnapshotProjection> type,
      @NonNull SnapshotSerializerId serId,
      @Nullable UUID aggregateUUID) {

    ScopedName classLevelKey =
        ScopedName.fromProjectionMetaData(type).with(repoType.id()).with(serId.name());

    // looks weird, but that was an err in the <0.8 version: the aggid (or UUID(0,0) was appended
    // without delimiter
    return ScopedName.of(
            classLevelKey.asString() + Optional.ofNullable(aggregateUUID).orElse(DEFAULT_UUID))
        .asString();
  }

  private LegacySnapshotKeys() {}
}
