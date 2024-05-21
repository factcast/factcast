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
package org.factcast.core.snap;

import java.util.Optional;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.store.FactStore;

@Deprecated
@Slf4j
public class FactCastSnapshotCache implements SnapshotCache {

  @NonNull private final FactStore store;

  public FactCastSnapshotCache(@NonNull FactStore store) {
    this.store = store;
    //noinspection deprecation
    log.warn(
        "You configured factus to use the default FactCastSnapshotCache instead of a client-local cache. This implemenation is deprecated and will be removed soon. Please consider alternative SnapshotCache implementations (like factcast-snapshotcache-redisson for instance).");
  }

  @Override
  public @NonNull Optional<Snapshot> getSnapshot(@NonNull SnapshotId id) {
    return store.getSnapshot(id);
  }

  @Override
  public void setSnapshot(@NonNull Snapshot snapshot) {
    store.setSnapshot(snapshot);
  }

  @Override
  public void clearSnapshot(@NonNull SnapshotId id) {
    store.clearSnapshot(id);
  }

  /** compacting will be controlled on server side, so this impl is empty. */
  public void compact(int retentionTimeInDays) {}
}
