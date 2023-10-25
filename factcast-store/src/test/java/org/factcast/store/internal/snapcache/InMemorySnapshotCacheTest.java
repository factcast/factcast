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
package org.factcast.store.internal.snapcache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.time.ZonedDateTime;
import java.util.UUID;
import org.factcast.core.snap.Snapshot;
import org.factcast.core.snap.SnapshotId;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class InMemorySnapshotCacheTest {
  @InjectMocks private InMemorySnapshotCache underTest;
  private final SnapshotId id = SnapshotId.of("foo", UUID.randomUUID());

  @Nested
  class WhenGettingSnapshot {

    @Test
    void happyCase() {
      final var snap = new Snapshot(id, UUID.randomUUID(), "foo".getBytes(), false);

      underTest.setSnapshot(snap);

      assertThat(underTest.getSnapshot(id)).isPresent().get().isSameAs(snap);
    }
  }

  @Nested
  class WhenClearingSnapshot {

    @Test
    void happyCase() {
      final var snap = new Snapshot(id, UUID.randomUUID(), "foo".getBytes(), false);

      underTest.setSnapshot(snap);
      underTest.clearSnapshot(id);

      assertThat(underTest.getSnapshot(id)).isEmpty();
    }
  }

  @Nested
  class WhenCompacting {

    @Test
    void happyCase() {
      final var snap = new Snapshot(id, UUID.randomUUID(), "foo".getBytes(), false);

      underTest.setSnapshot(snap);

      underTest.compact(ZonedDateTime.now().plusMinutes(1));

      assertThat(underTest.getSnapshot(id)).isEmpty();
    }
  }
}
