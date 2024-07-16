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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.factcast.factus.snapshot.Snapshot;
import org.factcast.factus.snapshot.SnapshotId;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SnapshotSerializationHelperTest {

  @Nested
  class WhenSerializingTo {
    final SnapshotId id = SnapshotId.of("theKey", UUID.randomUUID());
    final Snapshot s =
        new Snapshot(id, UUID.randomUUID(), "FooBarBaz".getBytes(StandardCharsets.UTF_8), false);
    final ByteArrayOutputStream os = new ByteArrayOutputStream();

    @Test
    void returnsCorrectSize() {
      long written = SnapshotSerializationHelper.serializeTo(s, os);

      byte[] bytes = os.toByteArray();

      assertThat(written).isEqualTo(bytes.length);
    }

    @Test
    void serializes() throws Exception {
      SnapshotSerializationHelper.serializeTo(s, os);
      byte[] bytes = os.toByteArray();
      Snapshot reread =
          (Snapshot) new ObjectInputStream(new ByteArrayInputStream(bytes)).readObject();

      Assertions.assertThat(reread).isEqualTo(s);
    }
  }
}
