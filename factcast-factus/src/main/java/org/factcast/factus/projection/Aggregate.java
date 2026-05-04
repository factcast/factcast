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
package org.factcast.factus.projection;

import java.util.Objects;
import java.util.UUID;
import lombok.*;

/**
 * Aggregates can either extend this or implement the Aggregate interface themselves. What also
 * needs to be provided for being an Aggregate is having a noArg-Constructor.
 */
@NoArgsConstructor
@AllArgsConstructor
public abstract class Aggregate implements SnapshotProjection {

  // is protected in order to be able to wrap it into a domain-specific ID in
  // the implementing class, think "PersonId".
  //
  // Also this is the reason, why this thing is called "aggregateId" rather
  // than "id".
  @Getter(AccessLevel.PROTECTED)
  // the setter has package level access in order to be used from
  // AggregateUtil, while not spoiling the public interface.
  @Setter(AccessLevel.PROTECTED)
  private UUID aggregateId;

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Aggregate aggregate = (Aggregate) o;
    return Objects.equals(aggregateId, aggregate.aggregateId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(aggregateId);
  }
}
