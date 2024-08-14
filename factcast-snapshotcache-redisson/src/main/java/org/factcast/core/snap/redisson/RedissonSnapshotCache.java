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

import java.util.*;
import java.util.concurrent.*;
import lombok.extern.slf4j.Slf4j;

@Slf4j
// TODO reimplement
public class RedissonSnapshotCache
// implements SnapshotCache
{
  //
  //  private static final String SNAPSHOT_CACHE_PREFIX = "SNAPC";
  //  final RedissonClient redisson;
  //  final ByteArrayCodec codec = new ByteArrayCodec();
  //
  //  private final RedissonSnapshotProperties properties;
  //
  //  public RedissonSnapshotCache(
  //      @NonNull RedissonClient redisson, @NonNull RedissonSnapshotProperties props) {
  //    this.redisson = redisson;
  //    this.properties = props;
  //  }
  //
  //  @Override
  //  public @NonNull Optional<SnapshotData> find(@NonNull SnapshotIdentifier id) {
  //    String key = createKeyFor(id);
  //
  //    // regular snapshot cache
  //    RBucket<byte[]> bucket = redisson.getBucket(key, codec);
  //    byte[] bytes = bucket.get();
  //    if (bytes!=null){ bucket.expireAsync(Duration.ofDays(properties.getRetentionTime()));
  //      return SnapshotData.from(bytes);
  //    }
  //
  //    // legacy
  //    RBucket<org.factcast.core.snap.Snapshot> legacyBucket =
  // properties.getSnapshotCacheRedissonCodec().getBucket(redisson, key);
  //    Optional<SnapshotData> snapshot =
  // Optional.ofNullable(legacyBucket.get()).map(SnapshotData::from);
  //    if (snapshot.isPresent()) {
  //      // renew TTL
  //      bucket.expireAsync(Duration.ofDays(properties.getRetentionTime()));
  //    }
  //    return snapshot;
  //  }
  //
  //  @Override
  //  public void store(@NonNull SnapshotData snapshot) {
  //    String key = createKeyFor(snapshot.id());
  //    RBucket<SnapshotData> bucket =
  // properties.getSnapshotCacheRedissonCodec().getBucket(redisson, key);
  //    bucket.set(snapshot, properties.getRetentionTime(), TimeUnit.DAYS);
  //  }
  //
  //  @Override
  //  public void remove(@NonNull SnapshotIdentifier id) {
  //    redisson.getBucket(createKeyFor(id)).delete();
  //  }
  //
  //  @NonNull
  //  @VisibleForTesting
  //  String createLegacyKeyFor(@NonNull SnapshotIdentifier id) {
  //    return id.key() + id.uuid();
  //  }
  //
  //  @NonNull
  //  @VisibleForTesting
  //  String createKeyFor(@NonNull SnapshotIdentifier id) {
  //    return SNAPSHOT_CACHE_PREFIX+ id.key() + id.uuid();
  //  }
}
