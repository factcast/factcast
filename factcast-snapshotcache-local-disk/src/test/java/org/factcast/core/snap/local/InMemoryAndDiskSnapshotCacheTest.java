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
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;
import org.factcast.core.snap.Snapshot;
import org.factcast.core.snap.SnapshotId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class InMemoryAndDiskSnapshotCacheTest {

  private InMemoryAndDiskSnapshotCache underTest;
  private final SnapshotDiskRepository diskRepository = mock(SnapshotDiskRepository.class);

  private final SnapshotId id = SnapshotId.of("foo", UUID.randomUUID());

  @BeforeEach
  void setUp() {
    InMemoryAndDiskSnapshotProperties props = new InMemoryAndDiskSnapshotProperties();
    underTest = new InMemoryAndDiskSnapshotCache(props, diskRepository);
  }

  @Nested
  class WhenSettingSnapshot {
    @Test
    void happyCase() {
      final Snapshot snap = new Snapshot(id, UUID.randomUUID(), "foo".getBytes(), false);

      underTest.setSnapshot(snap);

      Optional<Snapshot> snapshot = underTest.getSnapshot(id);
      assertThat(snapshot).isPresent();
      assertThat(snapshot.get()).isEqualTo(snap);
      verify(diskRepository, times(1)).save(snap);
    }
  }

  @Nested
  class WhenClearingSnapshot {

    @Test
    void happyCase() {
      final Snapshot snap = new Snapshot(id, UUID.randomUUID(), "foo".getBytes(), false);

      underTest.setSnapshot(snap);
      Optional<Snapshot> snapshot = underTest.getSnapshot(id);
      assertThat(snapshot).isPresent();
      assertThat(snapshot.get()).isEqualTo(snap);

      underTest.clearSnapshot(id);
      snapshot = underTest.getSnapshot(id);
      assertThat(snapshot).isEmpty();

      verify(diskRepository, times(1)).delete(id);
    }
  }

  @Nested
  class WhenGettingSnapshot {

    @Test
    void happyCase() {
      final Snapshot snap = new Snapshot(id, UUID.randomUUID(), "foo".getBytes(), false);

      underTest.setSnapshot(snap);

      Optional<Snapshot> snapshot = underTest.getSnapshot(id);
      assertThat(snapshot).isPresent();
      assertThat(snapshot.get()).isEqualTo(snap);
      verify(diskRepository, times(1)).save(snap);
      verify(diskRepository, never()).findById(id);
    }

    @Test
    void happyCase_fetchingFromDisk() {
      final Snapshot snap = new Snapshot(id, UUID.randomUUID(), "foo".getBytes(), false);

      when(diskRepository.findById(id)).thenReturn(Optional.of(snap));

      Optional<Snapshot> snapshot = underTest.getSnapshot(id);
      assertThat(snapshot).isPresent();
      assertThat(snapshot.get()).isEqualTo(snap);
      verify(diskRepository, never()).save(snap);
      verify(diskRepository, times(1)).findById(id);
    }
  }
}
