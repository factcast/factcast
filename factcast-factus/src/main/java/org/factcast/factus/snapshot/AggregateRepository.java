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
import org.factcast.factus.projection.Aggregate;

public class AggregateRepository extends AbstractSnapshotRepository {

  public AggregateRepository(
      SnapshotCache snapshotCache,
      SnapshotSerializerSelector selector,
      FactusMetrics factusMetrics) {
    super(snapshotCache, factusMetrics, selector);
  }

  public <T extends Aggregate> void store(@NonNull T aggregate, UUID state) {
    store(SnapshotIdentifier.from(aggregate), serialize(aggregate, state));
  }

  public <T extends Aggregate> @NonNull Optional<ProjectionAndState<T>> findLatest(
      @NonNull Class<T> type, @NonNull UUID aggregateId) {
    SnapshotIdentifier id = SnapshotIdentifier.of(type, aggregateId);
    return find(id)
        .map(
            sd ->
                ProjectionAndState.of(
                    deserialize(sd.serializedProjection(), type), sd.lastFactId()));
  }
}
