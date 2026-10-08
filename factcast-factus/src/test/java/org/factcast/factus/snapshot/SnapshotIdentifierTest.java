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
package org.factcast.factus.snapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.factcast.factus.projection.Aggregate;
import org.factcast.factus.projection.SnapshotProjection;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SnapshotIdentifierTest {

  static class A extends Aggregate {
    public A(UUID id) {
      aggregateId(id);
    }
  }

  static class S implements SnapshotProjection {}

  @Nested
  class WhenOfSnapshot {

    @Test
    void failsOnAggregate() {
      assertThatThrownBy(
              () -> {
                SnapshotIdentifier.of(A.class);
              })
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void succeeds() {
      SnapshotIdentifier id = SnapshotIdentifier.of(S.class);
      assertThat(id.aggregateId()).isNull();
      assertThat(id.projectionClass()).isEqualTo(S.class);
    }
  }

  @Nested
  class WhenOfAggregate {

    @Test
    void failsOnAggregate() {
      assertThatThrownBy(
              () -> {
                SnapshotIdentifier.of(A.class);
              })
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void succeeds() {
      UUID uuid = UUID.randomUUID();
      SnapshotIdentifier id = SnapshotIdentifier.of(A.class, uuid);
      assertThat(id.aggregateId()).isEqualTo(uuid);
      assertThat(id.projectionClass()).isEqualTo(A.class);
    }
  }

  @Nested
  class WhenFrom {
    private final String SERIALIZER_ID = "SERIALIZER_ID";

    @Test
    void fromAggregate() {
      UUID uuid = UUID.randomUUID();
      SnapshotIdentifier id = SnapshotIdentifier.from(new A(uuid));
      assertThat(id.projectionClass()).isEqualTo(A.class);
      assertThat(id.aggregateId()).isEqualTo(uuid);
    }

    @Test
    void fromSnapshot() {
      UUID uuid = UUID.randomUUID();
      SnapshotIdentifier id = SnapshotIdentifier.from(new S());
      assertThat(id.aggregateId()).isNull();
      assertThat(id.projectionClass()).isEqualTo(S.class);
    }

    @Test
    void fromAggregateAbuse() {
      UUID uuid = UUID.randomUUID();
      A agg = new A(uuid);
      assertThatThrownBy(() -> SnapshotIdentifier.from((SnapshotProjection) agg))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  class WhenRetrievingPossibleAggId {
    @Test
    void isNull() {
      assertThat(SnapshotIdentifier.of(S.class).aggIdAsStringOrNull()).isNull();
    }

    @Test
    void isNotNull() {
      final UUID aggId = UUID.randomUUID();
      assertThat(SnapshotIdentifier.of(A.class, aggId).aggIdAsStringOrNull())
          .isEqualTo(aggId.toString());
    }
  }
}
