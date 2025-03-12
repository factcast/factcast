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
package org.factcast.core.snap.redisson;

import com.google.common.annotations.VisibleForTesting;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.snap.Snapshot;
import org.factcast.factus.projection.*;
import org.factcast.factus.serializer.SnapshotSerializerId;
import org.factcast.factus.snapshot.SnapshotCache;
import org.factcast.factus.snapshot.SnapshotData;
import org.factcast.factus.snapshot.SnapshotIdentifier;
import org.factcast.factus.snapshot.SnapshotSerializerSelector;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.ByteArrayCodec;

@SuppressWarnings("deprecation")
@Slf4j
@RequiredArgsConstructor
public class RedissonSnapshotCache implements SnapshotCache {

  private static final String PREFIX = "sc_";

  final RedissonClient redisson;
  final SnapshotSerializerSelector selector;
  private final RedissonSnapshotProperties properties;

  @NonNull
  @VisibleForTesting
  String createKeyFor(@NonNull SnapshotIdentifier id) {
    return PREFIX
        + ScopedName.fromProjectionMetaData(id.projectionClass())
            .with(Optional.ofNullable(id.aggregateId()).map(UUID::toString).orElse("snapshot"))
            .asString();
  }

  @NonNull
  @VisibleForTesting
  String createLegacyKeyFor(@NonNull SnapshotIdentifier id) {
    UUID aggId = id.aggregateId();

    Class<? extends SnapshotProjection> type = id.projectionClass();
    SnapshotSerializerId serializerId = selector.selectSeralizerFor(type).id();

    return LegacySnapshotKeys.createKeyForType(id.projectionClass(), serializerId, aggId);
  }

  //////

  @Override
  public @NonNull Optional<SnapshotData> find(@NonNull SnapshotIdentifier id) {
    String key = createKeyFor(id);
    RBucket<byte[]> bucket = redisson.getBucket(key, ByteArrayCodec.INSTANCE);
    byte[] bytes = bucket.get();
    if (bytes != null && bytes.length > 0) {
      bucket.expireAsync(Duration.ofDays(properties.getRetentionTime()));
      return SnapshotData.from(bytes);
    } else {
      // find legacy snapshot
      String legacyKey = createLegacyKeyFor(id);
      RBucket<Snapshot> legacyBucket =
          properties.getSnapshotCacheRedissonCodec().getBucket(redisson, legacyKey);
      Optional<Snapshot> snapshot = Optional.ofNullable(legacyBucket.get());
      if (snapshot.isPresent()) {
        legacyBucket.expireAsync(Duration.ofDays(properties.getRetentionTime()));
      }
      Optional<SnapshotData> snapshotData =
          snapshot.map(
              s -> SnapshotData.from(s, selector.selectSeralizerFor(id.projectionClass()).id()));

      // migrate
      snapshotData.ifPresent(data -> store(id, data));

      return snapshotData;
    }
  }

  @Override
  public void store(@NonNull SnapshotIdentifier id, @NonNull SnapshotData snapshot) {
    redisson
        .getBucket(createKeyFor(id), ByteArrayCodec.INSTANCE)
        .set(snapshot.toBytes(), properties.getRetentionTime(), TimeUnit.DAYS);
  }

  @Override
  public void remove(@NonNull SnapshotIdentifier id) {
    redisson.getBucket(createKeyFor(id)).delete();
    redisson.getBucket(createLegacyKeyFor(id)).delete();
  }
}
