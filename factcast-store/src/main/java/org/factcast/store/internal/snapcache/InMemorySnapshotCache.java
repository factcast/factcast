/*
 * Copyright © 2017-2023 factcast.org
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
package org.factcast.store.internal.snapcache;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NonNull;
import org.apache.commons.collections4.map.LRUMap;
import org.factcast.core.snap.Snapshot;
import org.factcast.core.snap.SnapshotId;

public class InMemorySnapshotCache implements SnapshotCache {
  private static final int DEFAULT_CAPACITY = 100;

  private final Map<SnapshotId, SnapshotAndAccessTime> cache;

  public InMemorySnapshotCache() {
    this(DEFAULT_CAPACITY);
  }

  public InMemorySnapshotCache(int capacity) {
    cache = Collections.synchronizedMap(new LRUMap<>(Math.max(capacity, DEFAULT_CAPACITY)));
  }

  @Override
  public @NonNull Optional<Snapshot> getSnapshot(@NonNull SnapshotId id) {
    return Optional.ofNullable(cache.get(id)).map(SnapshotAndAccessTime::snapshot);
  }

  @Override
  public void setSnapshot(@NonNull Snapshot snap) {
    cache.put(snap.id(), new SnapshotAndAccessTime(snap, System.currentTimeMillis()));
  }

  @Override
  public void clearSnapshot(@NonNull SnapshotId id) {
    cache.remove(id);
  }

  @Override
  public void compact(@NonNull ZonedDateTime thresholdDate) {
    HashSet<Map.Entry<SnapshotId, SnapshotAndAccessTime>> copyOfEntries;

    synchronized (cache) {
      copyOfEntries = new HashSet<>(cache.entrySet());
    }

    final var thresholdMillis = thresholdDate.toInstant().toEpochMilli();
    copyOfEntries.forEach(
        e -> {
          SnapshotAndAccessTime snapshotAndAccessTime = e.getValue();
          if (thresholdMillis > snapshotAndAccessTime.accessTimeInMillis) {
            cache.remove(e.getKey());
          }
        });
  }

  @Data
  @AllArgsConstructor
  static class SnapshotAndAccessTime {
    Snapshot snapshot;
    long accessTimeInMillis;
  }
}
