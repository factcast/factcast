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
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.snap.Snapshot;
import org.factcast.core.snap.SnapshotCache;
import org.factcast.core.snap.SnapshotId;
import org.redisson.api.RBucket;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;

@Slf4j
public class RedissonSnapshotCache implements SnapshotCache {

  private static final String TS_INDEX = "SNAPCACHE_IDX";

  final RedissonClient redisson;

  private final int retentionTimeInDays;
  private final RMap<String, Long> index;

  public RedissonSnapshotCache(
      @NonNull RedissonClient redisson,
      int retentionTimeInDays) {
    this.redisson = redisson;
    this.retentionTimeInDays = retentionTimeInDays;
    index = redisson.getMap(TS_INDEX);
  }

  @Override
  public @NonNull Optional<Snapshot> getSnapshot(@NonNull SnapshotId id) {
    String key = createKeyFor(id);
    RBucket<Snapshot> bucket = redisson.getBucket(key);
    index.putAsync(key, System.currentTimeMillis());
    return Optional.ofNullable(bucket.get());
  }

  @Override
  public void setSnapshot(@NonNull Snapshot snapshot) {
    String key = createKeyFor(snapshot.id());
    index.putAsync(key, System.currentTimeMillis());
    redisson.getBucket(key).set(snapshot);
  }

  @Override
  public void clearSnapshot(@NonNull SnapshotId id) {
    redisson.getBucket(createKeyFor(id)).delete();
  }

  @Scheduled(cron = "${factcast.redis.snapshotCacheCompactCron:0 0 0 * * *}")
  public void compactTrigger() {
    compact(retentionTimeInDays);
  }

  @Override
  public void compact(int retentionTimeInDays) {
    Duration daysAgo = Duration.ofDays(retentionTimeInDays);
    long threshold = Instant.now().minus(daysAgo).toEpochMilli();
    removeEntriesUntouchedSince(threshold);
  }

  @VisibleForTesting
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
  private String createKeyFor(@NonNull SnapshotId id) {
    return id.key() + id.uuid();
  }
}
