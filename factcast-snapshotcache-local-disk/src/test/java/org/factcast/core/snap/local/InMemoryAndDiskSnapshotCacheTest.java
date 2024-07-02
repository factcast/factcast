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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.factcast.core.snap.Snapshot;
import org.factcast.core.snap.SnapshotId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class InMemoryAndDiskSnapshotCacheTest {

  private InMemoryAndDiskSnapshotCache underTest;

  private final SnapshotId id = SnapshotId.of("foo", UUID.randomUUID());

  @BeforeEach
  void setUp() {
    InMemoryAndDiskSnapshotProperties props = new InMemoryAndDiskSnapshotProperties();
    underTest = new InMemoryAndDiskSnapshotCache(props);
  }

  @Test
  void happyCase() {
    final Snapshot snap = new Snapshot(id, UUID.randomUUID(), "foo".getBytes(), false);

    underTest.setSnapshot(snap);

    assertThat(underTest.getSnapshot(id)).isPresent().get().isEqualTo(snap);
  }

  @Nested
  class WhenClearingSnapshot {

    @Test
    void happyCase() {
      final Snapshot snap = new Snapshot(id, UUID.randomUUID(), "foo".getBytes(), false);

      underTest.setSnapshot(snap);
      underTest.clearSnapshot(id);

      assertThat(underTest.getSnapshot(id)).isEmpty();
    }
  }

  //  @Nested
  //  class WhenGCing {
  //
  //    @Test
  //    void happyCase() throws InterruptedException {
  //      UUID lastFact = UUID.randomUUID();
  //      Snapshot snap = new Snapshot(id, lastFact, "foo".getBytes(), false);
  //
  //      underTest.setSnapshot(snap);
  //
  //      // Try to get this garbage collected
  //      snap = null;
  //      System.gc();
  //      Thread.sleep(2000);
  //
  //      assertThat(underTest.getSnapshot(id)).isEmpty();
  //    }
  //  }
}
