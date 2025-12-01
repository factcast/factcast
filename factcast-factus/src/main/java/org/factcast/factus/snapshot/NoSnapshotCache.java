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
package org.factcast.factus.snapshot;

import java.util.Optional;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * only usable if you do not use snapshots at all. Most clients will need to configure a
 * SnapshotCache.
 */
@Slf4j
public class NoSnapshotCache implements SnapshotCache {

  public NoSnapshotCache() {
    log.info(
        "No Snapshot Cache has been configured. If there is an attempt to store a snapshot this will throw a UnsupportedOperationException. If you use Snapshots, please choose one of the existing implementations or provide your own.");
  }

  @Override
  public @NonNull Optional<SnapshotData> find(@NonNull SnapshotIdentifier id) {
    return Optional.empty();
  }

  @Override
  public void store(@NonNull SnapshotIdentifier id, @NonNull SnapshotData snapshot) {
    fail();
  }

  @Override
  public void remove(@NonNull SnapshotIdentifier id) {
    fail();
  }

  private static void fail() {
    throw new UnsupportedOperationException(
        "NoSnapshotCache has been configured. See https://docs.factcast.org/usage/factus/projections/snapshots/snapshot-caching/");
  }
}
