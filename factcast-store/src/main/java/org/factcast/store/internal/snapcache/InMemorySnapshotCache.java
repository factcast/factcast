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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NonNull;
import org.factcast.core.snap.Snapshot;
import org.factcast.core.snap.SnapshotId;

/** This class is used only in read-only mode. */
public class InMemorySnapshotCache implements SnapshotCache {

  private final Cache<SnapshotId, SnapshotAndAccessTime> cache =
      CacheBuilder.newBuilder().softValues().build();

  @Override
  public @NonNull Optional<Snapshot> getSnapshot(@NonNull SnapshotId id) {
    return Optional.ofNullable(cache.getIfPresent(id)).map(SnapshotAndAccessTime::snapshot);
  }

  @Override
  public void setSnapshot(@NonNull Snapshot snap) {
    cache.put(snap.id(), new SnapshotAndAccessTime(snap, System.currentTimeMillis()));
  }

  @Override
  public void clearSnapshot(@NonNull SnapshotId id) {
    cache.invalidate(id);
  }

  @Override
  public void compact(@NonNull ZonedDateTime thresholdDate) {
    HashSet<Map.Entry<SnapshotId, SnapshotAndAccessTime>> copyOfEntries;

    synchronized (cache) {
      copyOfEntries = new HashSet<>(cache.asMap().entrySet());
    }

    final var thresholdMillis = thresholdDate.toInstant().toEpochMilli();
    copyOfEntries.forEach(
        e -> {
          SnapshotAndAccessTime snapshotAndAccessTime = e.getValue();
          if (thresholdMillis > snapshotAndAccessTime.accessTimeInMillis) {
            cache.invalidate(e.getKey());
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
