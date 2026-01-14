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

import com.google.common.base.Preconditions;
import jakarta.annotation.Nullable;
import java.util.UUID;
import lombok.NonNull;
import lombok.Value;
import org.factcast.factus.projection.Aggregate;
import org.factcast.factus.projection.AggregateUtil;
import org.factcast.factus.projection.SnapshotProjection;

@Value
public class SnapshotIdentifier {

  @NonNull Class<? extends SnapshotProjection> projectionClass;
  @Nullable UUID aggregateId; // only used if created for an Aggregate

  public static SnapshotIdentifier of(
      @NonNull Class<? extends SnapshotProjection> snapshotProjectionType) {
    Preconditions.checkArgument(
        !Aggregate.class.isAssignableFrom(snapshotProjectionType),
        "SnapshotIdentifiers for Aggregates must contain a UUID");
    return new SnapshotIdentifier(snapshotProjectionType, null);
  }

  public static SnapshotIdentifier of(
      @NonNull Class<? extends Aggregate> aggregateType, @NonNull UUID aggregateId) {
    return new SnapshotIdentifier(aggregateType, aggregateId);
  }

  public static SnapshotIdentifier from(@NonNull SnapshotProjection snapProj) {
    Preconditions.checkArgument(
        !(snapProj instanceof Aggregate), "SnapshotIdentifiers for Aggregates must contain a UUID");
    return new SnapshotIdentifier(snapProj.getClass(), null);
  }

  public static SnapshotIdentifier from(@NonNull Aggregate agg) {
    return new SnapshotIdentifier(agg.getClass(), AggregateUtil.aggregateId(agg));
  }
}
