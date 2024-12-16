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
import org.factcast.factus.projection.SnapshotProjection;
import org.factcast.factus.serializer.ProjectionMetaData;
import org.factcast.factus.serializer.SnapshotSerializerId;
import org.factcast.factus.snapshot.SnapshotData;
import org.factcast.factus.snapshot.SnapshotIdentifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class InMemoryAndDiskSnapshotCacheTest {

  private InMemoryAndDiskSnapshotCache underTest;
  private final SnapshotDiskRepository diskRepository = mock(SnapshotDiskRepository.class);

  @ProjectionMetaData(name = "foo", revision = 1)
  class foo implements SnapshotProjection {}

  SnapshotIdentifier id = new SnapshotIdentifier(foo.class, UUID.randomUUID());

  final SnapshotData snap =
      new SnapshotData("foo".getBytes(), SnapshotSerializerId.of("name"), UUID.randomUUID());

  @BeforeEach
  void setUp() {
    underTest = new InMemoryAndDiskSnapshotCache(diskRepository);
  }

  @Nested
  class WhenSettingSnapshot {
    @Test
    void happyCase() {
      underTest.store(id, snap);

      Optional<SnapshotData> snapshot = underTest.find(id);
      assertThat(snapshot).isPresent();
      assertThat(snap).isEqualTo(snapshot.get());
      verify(diskRepository, times(1)).save(id, snap);
    }
  }

  @Nested
  class WhenClearingSnapshot {

    @Test
    void happyCase() {
      underTest.store(id, snap);
      Optional<SnapshotData> snapshot = underTest.find(id);
      assertThat(snapshot).isPresent();
      assertThat(snap).isEqualTo(snapshot.get());

      underTest.remove(id);
      snapshot = underTest.find(id);
      assertThat(snapshot).isEmpty();

      verify(diskRepository, times(1)).delete(id);
    }
  }

  @Nested
  class WhenGettingSnapshot {

    @Test
    void happyCase() {
      underTest.store(id, snap);

      Optional<SnapshotData> snapshot = underTest.find(id);
      assertThat(snapshot).isPresent();
      assertThat(snap).isEqualTo(snapshot.get());
      verify(diskRepository, times(1)).save(id, snap);
      verify(diskRepository, never()).findById(id);
    }

    @Test
    void happyCase_fetchingFromDisk() {
      when(diskRepository.findById(id)).thenReturn(Optional.of(snap));

      Optional<SnapshotData> snapshot = underTest.find(id);
      assertThat(snapshot).isPresent();
      assertThat(snap).isEqualTo(snapshot.get());
      verify(diskRepository, never()).save(id, snap);
      verify(diskRepository, times(1)).findById(id);
    }

    @Test
    void happyCase_exceptionFromDisk() {
      when(diskRepository.findById(id)).thenThrow(new RuntimeException());

      Optional<SnapshotData> snapshot = underTest.find(id);
      assertThat(snapshot).isEmpty();
    }
  }
}
