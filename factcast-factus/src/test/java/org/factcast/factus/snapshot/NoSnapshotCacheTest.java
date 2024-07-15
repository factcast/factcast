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

import lombok.NonNull;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NoSnapshotCacheTest {

  @InjectMocks private NoSnapshotCache underTest;

  @Nested
  class WhenSettingSnapshot {
    @Mock private @NonNull SnapshotId id;

    @Test
    void failsIfSetIsCalled() {
      Snapshot mock = Mockito.mock(Snapshot.class);
      Assertions.assertThatThrownBy(
              () -> {
                underTest.setSnapshot(mock);
              })
          .isInstanceOf(UnsupportedOperationException.class);
    }
  }

  @Nested
  class WhenGettingSnapshot {
    @Mock private @NonNull SnapshotId id;

    @Test
    void returnsEmptyOnGet() {
      Assertions.assertThat(underTest.getSnapshot(id)).isEmpty();
    }
  }

  @Nested
  class WhenClearingSnapshot {
    @Mock private @NonNull SnapshotId id;

    @Test
    void failsIfClearIsCalled() {
      Assertions.assertThatThrownBy(
              () -> {
                underTest.clearSnapshot(id);
              })
          .isInstanceOf(UnsupportedOperationException.class);
    }
  }
}
