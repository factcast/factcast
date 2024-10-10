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

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.factcast.core.snap.Snapshot;
import org.factcast.core.snap.SnapshotId;
import org.factcast.factus.serializer.SnapshotSerializerId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@SuppressWarnings("deprecation")
@ExtendWith(MockitoExtension.class)
class SnapshotDataTest {

  @Test
  void symetry() {
    SnapshotData snapshotData =
        new SnapshotData(
            "foo".getBytes(StandardCharsets.UTF_8),
            SnapshotSerializerId.of("test"),
            new UUID(1, 2));
    byte[] ba = snapshotData.toBytes();

    SnapshotData actual = SnapshotData.from(ba).orElseThrow(IllegalArgumentException::new);

    Assertions.assertThat(actual.serializedProjection())
        .isEqualTo(snapshotData.serializedProjection());
    Assertions.assertThat(actual.lastFactId()).isEqualTo(snapshotData.lastFactId());
    Assertions.assertThat(actual.snapshotSerializerId())
        .isEqualTo(snapshotData.snapshotSerializerId());
  }

  @Test
  void wrongMagic() {
    SnapshotData snapshotData =
        new SnapshotData(
            "foo".getBytes(StandardCharsets.UTF_8),
            SnapshotSerializerId.of("test"),
            new UUID(1, 2));
    byte[] ba = snapshotData.toBytes();
    ba[0] = 0;

    assertThat(SnapshotData.from(ba)).isEmpty();
  }

  @Test
  void fromLegacy() {
    UUID state = new UUID(1, 2);
    SnapshotSerializerId serId = SnapshotSerializerId.of("test");
    byte[] bytes = "foo".getBytes(StandardCharsets.UTF_8);
    SnapshotId id = SnapshotId.of("id", new UUID(0, 0));

    SnapshotData data = SnapshotData.from(new Snapshot(id, state, bytes, false), serId);
    Assertions.assertThat(data.serializedProjection()).isEqualTo(bytes);
    Assertions.assertThat(data.snapshotSerializerId()).isEqualTo(serId);
    Assertions.assertThat(data.lastFactId()).isEqualTo(state);
  }
}
