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
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.snap.Snapshot;
import org.factcast.core.snap.SnapshotCache;
import org.factcast.core.snap.SnapshotId;
import org.redisson.api.RBucket;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.Codec;
import org.redisson.codec.MarshallingCodec;

@Slf4j
public class RedissonSnapshotCache implements SnapshotCache {

  final RedissonClient redisson;

  private final int retentionTimeInDays;

  // still needed to remove stale snapshots, can be removed at some point
  private static final String TS_INDEX = "SNAPCACHE_IDX";
  private final RMap<String, Long> index;

  public RedissonSnapshotCache(@NonNull RedissonClient redisson, int retentionTimeInDays) {
    this.redisson = redisson;
    this.retentionTimeInDays = retentionTimeInDays;
    index = redisson.getMap(TS_INDEX);
  }

  @Override
  public @NonNull Optional<Snapshot> getSnapshot(@NonNull SnapshotId id) {
    String key = createKeyFor(id);
    // From redisson-spring-boot-starter 3.19.0 onwards the default codec is Kryo5.
    // Since it also uses Java17 we have to stick with 3.18.1 and enforce the old default codec manually.
    Codec codec = new MarshallingCodec();
    RBucket<Snapshot> bucket = redisson.getBucket(key, codec);

    Optional<Snapshot> snapshot = Optional.ofNullable(bucket.get());
    if (snapshot.isPresent()) {
      // renew TTL
      bucket.expireAsync(retentionTimeInDays, TimeUnit.DAYS);
      // can be removed at some point
      index.removeAsync(key, System.currentTimeMillis());
    }
    return snapshot;
  }

  @Override
  public void setSnapshot(@NonNull Snapshot snapshot) {
    String key = createKeyFor(snapshot.id());
    redisson.getBucket(key).set(snapshot, retentionTimeInDays, TimeUnit.DAYS);
  }

  @Override
  public void clearSnapshot(@NonNull SnapshotId id) {
    redisson.getBucket(createKeyFor(id)).delete();
  }

  @Override
  @Deprecated // using EXPIRE instead
  public void compact(int retentionTimeInDays) {
    Duration daysAgo = Duration.ofDays(retentionTimeInDays);
    long threshold = Instant.now().minus(daysAgo).toEpochMilli();
    removeEntriesUntouchedSince(threshold);
  }

  @VisibleForTesting
  @Deprecated
  public void removeEntriesUntouchedSince(long threshold) {
    index
        .readAllEntrySet()
        .forEach(
            e -> {
              if (e.getValue() < threshold) {
                String key = e.getKey();
                redisson.getBucket(key).deleteAsync();
                index.removeAsync(key);
              }
            });
  }

  @NonNull
  @VisibleForTesting
  String createKeyFor(@NonNull SnapshotId id) {
    return id.key() + id.uuid();
  }
}
