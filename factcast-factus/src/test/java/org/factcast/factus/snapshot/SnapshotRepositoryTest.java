/*
 * Copyright © 2017-2025 factcast.org
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

import java.util.*;
import lombok.NonNull;
import org.assertj.core.api.Assertions;
import org.factcast.factus.metrics.FactusMetrics;
import org.factcast.factus.projection.*;
import org.factcast.factus.serializer.SnapshotSerializerId;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SnapshotRepositoryTest {

  @Mock SnapshotCache snapshotCache;
  @Mock FactusMetrics factusMetrics;
  @Mock SnapshotSerializerSelector selector;
  @InjectMocks SnapshotRepository underTest;

  @Nested
  class WhenFinding {
    @NonNull SnapshotIdentifier id = SnapshotIdentifier.of(SnapshotProjection.class);

    @Test
    void returnsEmptyWhenDeserializationFails() {
      Mockito.when(snapshotCache.find(Mockito.any()))
          .thenReturn(
              Optional.of(
                  new SnapshotData(
                      new byte[0], SnapshotSerializerId.of("guess"), UUID.randomUUID())));
      Mockito.when(selector.selectSeralizerFor(Mockito.any())).thenReturn(new FailingSerializer());
      Assertions.assertThat(underTest.findAndDeserialize(SnapshotProjection.class, id)).isEmpty();
    }
  }
}
