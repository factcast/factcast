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
package org.factcast.core.snap.local;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.Optional;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.factcast.factus.snapshot.Snapshot;
import org.factcast.factus.snapshot.SnapshotCache;
import org.factcast.factus.snapshot.SnapshotId;

@Slf4j
public class InMemoryAndDiskSnapshotCache implements SnapshotCache {
  private final Cache<SnapshotId, Snapshot> cache;
  private final SnapshotDiskRepository snapshotDiskRepository;

  public InMemoryAndDiskSnapshotCache(
      InMemoryAndDiskSnapshotProperties props, SnapshotDiskRepository snapshotDiskRepository) {
    cache =
        Caffeine.newBuilder()
            .softValues()
            // .expireAfterAccess(Duration.ofDays(props.getDeleteSnapshotStaleForDays()))
            .build();

    this.snapshotDiskRepository = snapshotDiskRepository;
  }

  @Override
  public @NonNull Optional<Snapshot> getSnapshot(@NonNull SnapshotId id) {
    Optional<Snapshot> snapshotOpt = Optional.ofNullable(cache.getIfPresent(id));

    if (!snapshotOpt.isPresent()) {
      try {
        snapshotOpt = snapshotDiskRepository.findById(id);
        snapshotOpt.ifPresent(snapshot -> cache.put(id, snapshot));
      } catch (Exception e) {
        log.error("Error retrieving snapshot with id: {}", id, e);
      }
    }

    return snapshotOpt;
  }

  @Override
  public void setSnapshot(@NonNull Snapshot snapshot) {
    snapshotDiskRepository.save(snapshot);
    cache.put(snapshot.id(), snapshot);
  }

  @Override
  public void clearSnapshot(@NonNull SnapshotId id) {
    snapshotDiskRepository.delete(id);
    cache.invalidate(id);
  }
}
