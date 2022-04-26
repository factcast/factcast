/*
 * Copyright © 2017-2022 factcast.org
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
package org.factcast.core.snap;

import static org.mockito.Mockito.*;

import lombok.NonNull;
import org.factcast.core.store.FactStore;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FactCastSnapshotCacheTest {

  @Mock private @NonNull FactStore store;
  @InjectMocks private FactCastSnapshotCache underTest;

  @Nested
  class WhenGettingSnapshot {
    @Mock private @NonNull SnapshotId id;

    @Test
    void delegates() {
      underTest.getSnapshot(id);
      verify(store).getSnapshot(id);
    }
  }

  @Nested
  class WhenSettingSnapshot {
    @Mock private Snapshot snap;

    @Test
    void delegates() {
      underTest.setSnapshot(snap);
      verify(store).setSnapshot(snap);
    }
  }

  @Nested
  class WhenClearingSnapshot {
    @Mock private @NonNull SnapshotId id;

    @Test
    void delegates() {
      underTest.clearSnapshot(id);
      verify(store).clearSnapshot(id);
    }
  }
}
