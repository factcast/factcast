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
package org.factcast.factus.snapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import org.factcast.factus.projection.SnapshotProjection;
import org.factcast.factus.serializer.MyDefaultSnapshotSerializer;
import org.factcast.factus.serializer.OtherSnapSer;
import org.factcast.factus.serializer.SnapSerWithoutNoArgsConstructor;
import org.factcast.factus.serializer.SnapshotSerializer;
import org.junit.jupiter.api.*;

class SnapshotSerializerSupplierTest {

  private final SnapshotSerializer defaultSerializer = new MyDefaultSnapshotSerializer();

  private final SnapshotSerializerSelector underTest =
      new SnapshotSerializerSelector(defaultSerializer, new SnapshotSerializerSupplier.Default());

  @Nested
  class WhenRetrievingSerializer {

    @Test
    void defaultSerializer() {
      assertThat(underTest.selectSeralizerFor(ProjectionWithDefaultSerializer.class))
          .isSameAs(defaultSerializer);
    }

    @Test
    void alternativeSerializer() {
      assertThat(underTest.selectSeralizerFor(ProjectionWithAlternateSerializer.class))
          .isInstanceOf(OtherSnapSer.class);
    }

    @Test
    void failIfSerializerCannotBeCreated() {
      assertThrows(
          SerializerInstantiationException.class,
          () -> underTest.selectSeralizerFor(ProjectionWithBrokenSerializer.class));
    }
  }

  @SerializeUsing(SnapSerWithoutNoArgsConstructor.class)
  static class ProjectionWithBrokenSerializer implements SnapshotProjection {}

  @SerializeUsing(OtherSnapSer.class)
  static class ProjectionWithAlternateSerializer implements SnapshotProjection {}

  static class ProjectionWithDefaultSerializer implements SnapshotProjection {}
}
