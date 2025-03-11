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
import java.util.UUID;
import lombok.NonNull;
import org.factcast.factus.metrics.FactusMetrics;
import org.factcast.factus.projection.SnapshotProjection;

public class SnapshotRepository extends AbstractSnapshotRepository {

  public SnapshotRepository(
      @NonNull SnapshotCache snapshotCache,
      @NonNull SnapshotSerializerSelector selector,
      FactusMetrics factusMetrics) {
    super(snapshotCache, factusMetrics, selector);
  }

  public void store(@NonNull SnapshotProjection projection, @NonNull UUID state) {
    store(SnapshotIdentifier.from(projection), serialize(projection, state));
  }

  public <T extends SnapshotProjection> @NonNull Optional<ProjectionAndState<T>> findLatest(
      @NonNull Class<T> type) {
    return findAndDeserialize(type, SnapshotIdentifier.of(type));
  }
}
