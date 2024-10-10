/*
 * Copyright © 2017-2023 factcast.org
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

import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.factcast.factus.projection.SnapshotProjection;
import org.factcast.factus.serializer.SnapshotSerializerId;
import org.factcast.factus.snapshot.SnapshotData;
import org.factcast.factus.snapshot.SnapshotIdentifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class InMemorySnapshotCacheTest {
  private InMemorySnapshotCache underTest;

  private final SnapshotIdentifier id = SnapshotIdentifier.of(SnapshotProjection.class);
  private final SnapshotSerializerId serId = SnapshotSerializerId.of("buh");

  @BeforeEach
  void setUp() {
    InMemorySnapshotProperties props = new InMemorySnapshotProperties();
    underTest = new InMemorySnapshotCache(props);
  }

  @Nested
  class WhenGettingSnapshot {

    @Test
    void happyCase() {
      final SnapshotData snap = new SnapshotData("foo".getBytes(), serId, UUID.randomUUID());
      underTest.store(id, snap);

      Assertions.assertThat(underTest.find(id)).isPresent().get().isEqualTo(snap);
    }
  }

  @Nested
  class WhenClearingSnapshot {

    @Test
    void happyCase() {
      final SnapshotData snap = new SnapshotData("foo".getBytes(), serId, UUID.randomUUID());

      underTest.store(id, snap);
      Assertions.assertThat(underTest.find(id)).isNotEmpty();

      underTest.remove(id);
      Assertions.assertThat(underTest.find(id)).isEmpty();
    }
  }
}
