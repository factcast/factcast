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

@Slf4j
public class NoopSnapshotCache implements SnapshotCache {

  public NoopSnapshotCache() {
    log.info(
        "Using NoOp Snapshot Cache. Used for when you don't want to cache snapshots. If there is an attempt to store an snapshot this will throw a UnsupportedOperationException");
  }

  @Override
  public @NonNull Optional<Snapshot> getSnapshot(@NonNull SnapshotId id) {
    throw new UnsupportedOperationException("NoOpSnapshotCache does not support getSnapshot");
  }

  @Override
  public void setSnapshot(@NonNull Snapshot snapshot) {
    throw new UnsupportedOperationException("NoOpSnapshotCache does not support setSnapshot");
  }

  @Override
  public void clearSnapshot(@NonNull SnapshotId id) {
    throw new UnsupportedOperationException("NoOpSnapshotCache does not support clearSnapshot");
  }

  @Override
  public void compact(int retentionTimeInDays) {
    throw new UnsupportedOperationException("NoOpSnapshotCache does not support compact");
  }
}
