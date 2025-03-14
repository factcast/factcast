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

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.Optional;
import lombok.NonNull;
import org.factcast.factus.snapshot.SnapshotCache;
import org.factcast.factus.snapshot.SnapshotData;
import org.factcast.factus.snapshot.SnapshotIdentifier;

public class InMemorySnapshotCache implements SnapshotCache {

  private final Cache<SnapshotIdentifier, SnapshotData> cache;

  public InMemorySnapshotCache(InMemorySnapshotProperties props) {
    cache =
        Caffeine.newBuilder()
            .softValues()
            .expireAfterAccess(Duration.ofDays(props.getDeleteSnapshotStaleForDays()))
            .build();
  }

  @Override
  public @NonNull Optional<SnapshotData> find(@NonNull SnapshotIdentifier id) {
    return Optional.ofNullable(cache.getIfPresent(id));
  }

  @Override
  public void store(@NonNull SnapshotIdentifier id, @NonNull SnapshotData snapshot) {
    cache.put(id, snapshot);
  }

  @Override
  public void remove(@NonNull SnapshotIdentifier id) {
    cache.invalidate(id);
  }
}
