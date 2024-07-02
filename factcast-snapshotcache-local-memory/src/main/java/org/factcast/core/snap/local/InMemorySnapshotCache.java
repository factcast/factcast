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
package org.factcast.core.snap.local;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.time.Duration;
import java.util.Optional;
import lombok.NonNull;
import org.factcast.core.snap.Snapshot;
import org.factcast.core.snap.SnapshotCache;
import org.factcast.core.snap.SnapshotId;

public class InMemorySnapshotCache implements SnapshotCache {

  private final Cache<SnapshotId, Snapshot> cache;

  public InMemorySnapshotCache(InMemorySnapshotProperties props) {
    cache =
        CacheBuilder.newBuilder()
            .softValues()
            .expireAfterAccess(Duration.ofDays(props.getDeleteSnapshotStaleForDays()))
            .build();
  }

  @Override
  public @NonNull Optional<Snapshot> getSnapshot(@NonNull SnapshotId id) {
    return Optional.ofNullable(cache.getIfPresent(id));
  }

  @Override
  public void setSnapshot(@NonNull Snapshot snap) {
    cache.put(snap.id(), snap);
  }

  @Override
  public void clearSnapshot(@NonNull SnapshotId id) {
    cache.invalidate(id);
  }

  @Override
  public void compact(int retentionTimeInDays) {
    // Handled by Guava cache
  }
}
