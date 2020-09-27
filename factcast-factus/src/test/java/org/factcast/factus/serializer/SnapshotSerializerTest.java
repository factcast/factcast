/*
 * Copyright © 2017-2020 factcast.org
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
package org.factcast.factus.serializer;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.Data;
import org.factcast.factus.projection.SnapshotProjection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SnapshotSerializerTest {

  private final SnapshotSerializer underTest = new SnapshotSerializer.DefaultSnapshotSerializer();

  @Test
  void testRoundtrip() {
    // RUN
    SimpleSnapshotProjection initialProjection = new SimpleSnapshotProjection();
    initialProjection.val("Hello");

    byte[] bytes = underTest.serialize(initialProjection);
    SimpleSnapshotProjection projection =
        underTest.deserialize(SimpleSnapshotProjection.class, bytes);

    // ASSERT
    assertThat(projection.val()).isEqualTo("Hello");
  }

  @Test
  void testCompressionProperty() {
    assertThat(underTest.includesCompression()).isFalse();
  }

  @Data
  static class SimpleSnapshotProjection implements SnapshotProjection {
    String val;
  }
}
