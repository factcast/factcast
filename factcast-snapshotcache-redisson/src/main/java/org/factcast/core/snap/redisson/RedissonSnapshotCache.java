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
import java.util.*;
import java.util.concurrent.*;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.snap.Snapshot;
import org.factcast.core.snap.SnapshotId;
import org.factcast.factus.snapshot.SnapshotCache;
import org.redisson.api.RBucket;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;

@Slf4j
public class RedissonSnapshotCache implements SnapshotCache {

  final RedissonClient redisson;

  private final RedissonSnapshotProperties properties;

  // still needed to remove stale snapshots, can be removed at some point
  private static final String TS_INDEX = "SNAPCACHE_IDX";
  private final RMap<String, Long> index;

  public RedissonSnapshotCache(
      @NonNull RedissonClient redisson, @NonNull RedissonSnapshotProperties props) {
    this.redisson = redisson;
    this.properties = props;

    index = properties.getSnapshotCacheRedissonCodec().getMap(redisson, TS_INDEX);
  }

  @Override
  public @NonNull Optional<Snapshot> getSnapshot(@NonNull SnapshotId id) {
    String key = createKeyFor(id);

    RBucket<Snapshot> bucket = properties.getSnapshotCacheRedissonCodec().getBucket(redisson, key);
    Optional<Snapshot> snapshot = Optional.ofNullable(bucket.get());
    if (snapshot.isPresent()) {
      // renew TTL
      bucket.expireAsync(Duration.ofDays(properties.getRetentionTime()));
      // can be removed at some point
      index.removeAsync(key, System.currentTimeMillis());
    }
    return snapshot;
  }

  @Override
  public void setSnapshot(@NonNull Snapshot snapshot) {
    String key = createKeyFor(snapshot.id());
    RBucket<Snapshot> bucket = properties.getSnapshotCacheRedissonCodec().getBucket(redisson, key);
    bucket.set(snapshot, properties.getRetentionTime(), TimeUnit.DAYS);
  }

  @Override
  public void clearSnapshot(@NonNull SnapshotId id) {
    redisson.getBucket(createKeyFor(id)).delete();
  }

  @NonNull
  @VisibleForTesting
  String createKeyFor(@NonNull SnapshotId id) {
    return id.key() + id.uuid();
  }
}
