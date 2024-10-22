/*
 * Copyright © 2017-2024 factcast.org
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

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.factcast.factus.snapshot.SnapshotData;
import org.factcast.factus.snapshot.SnapshotIdentifier;

public interface SnapshotDiskRepository {
  CompletableFuture<Void> save(SnapshotIdentifier id, SnapshotData snapshot);

  Optional<SnapshotData> findById(SnapshotIdentifier id);

  CompletableFuture<Void> delete(SnapshotIdentifier id);
}
