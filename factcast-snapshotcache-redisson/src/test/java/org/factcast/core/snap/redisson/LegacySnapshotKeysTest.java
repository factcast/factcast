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
package org.factcast.core.snap.redisson;

import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.factcast.factus.projection.Aggregate;
import org.factcast.factus.projection.SnapshotProjection;
import org.factcast.factus.serializer.ProjectionMetaData;
import org.factcast.factus.serializer.SnapshotSerializerId;
import org.junit.jupiter.api.Test;

class LegacySnapshotKeysTest {

  @Test
  void snapshots() {
    Class<? extends SnapshotProjection> t = SomeSnapshotProjection.class;
    SnapshotSerializerId serId = SnapshotSerializerId.of("JacksonSnapshotSerializer");
    Assertions.assertThat(LegacySnapshotKeys.createKeyForType(t, serId))
        .isEqualTo(
            "org.factcast.core.snap.redisson.LegacySnapshotKeysTest$SomeSnapshotProjection_3_ProjectionSnapshotRepositoryImpl_JacksonSnapshotSerializer00000000-0000-0000-0000-000000000000");
  }

  @Test
  void aggregates() {
    Class<? extends SnapshotProjection> t = SomeAggregate.class;
    SnapshotSerializerId serId = SnapshotSerializerId.of("JacksonSnapshotSerializer");
    Assertions.assertThat(LegacySnapshotKeys.createKeyForType(t, serId, new UUID(0, 1)))
        .isEqualTo(
            "org.factcast.core.snap.redisson.LegacySnapshotKeysTest$SomeAggregate_4_AggregateSnapshotRepositoryImpl_JacksonSnapshotSerializer00000000-0000-0000-0000-000000000001");
  }

  @Test
  void snapshotsWith0Id() {
    Class<? extends SnapshotProjection> t = SomeSnapshotProjection.class;
    SnapshotSerializerId serId = SnapshotSerializerId.of("JacksonSnapshotSerializer");
    Assertions.assertThat(LegacySnapshotKeys.createKeyForType(t, serId, new UUID(0, 0)))
        .isEqualTo(
            "org.factcast.core.snap.redisson.LegacySnapshotKeysTest$SomeSnapshotProjection_3_ProjectionSnapshotRepositoryImpl_JacksonSnapshotSerializer00000000-0000-0000-0000-000000000000");
  }

  @ProjectionMetaData(revision = 3)
  static class SomeSnapshotProjection implements SnapshotProjection {}

  @ProjectionMetaData(revision = 4)
  static class SomeAggregate extends Aggregate {}
}
