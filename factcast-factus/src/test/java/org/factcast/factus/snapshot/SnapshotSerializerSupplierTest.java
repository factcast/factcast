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

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.factcast.factus.projection.SnapshotProjection;
import org.factcast.factus.serializer.MyDefaultSnapshotSerializer;
import org.factcast.factus.serializer.OtherSnapSer;
import org.factcast.factus.serializer.SnapSerWithoutNoArgsConstructor;
import org.factcast.factus.serializer.SnapshotSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.google.common.collect.Lists;

import lombok.NonNull;

class SnapshotSerializerSupplierTest {

  @NonNull SnapshotSerializer defaultSerializer = new MyDefaultSnapshotSerializer();

  SnapshotSerializerSupplier underTest =
      new SnapshotSerializerSupplier(defaultSerializer, emptyList());

  @Nested
  class WhenRetrievingSerializer {

    @BeforeEach
    void setup() {}

    @Test
    void defaultSerializer() {
      assertThat(underTest.retrieveSerializer(ProjectionWithDefaultSerializer.class))
          .isSameAs(defaultSerializer);
    }

    @Test
    void alternativeSerializer() {
      assertThat(underTest.retrieveSerializer(ProjectionWithTwoSerializers.class))
          .isInstanceOf(OtherSnapSer.class);
    }

    @Test
    void alternativeSerializerFromList() {
      underTest =
          new SnapshotSerializerSupplier(
              defaultSerializer, Lists.newArrayList(new SnapSerWithoutNoArgsConstructor("test")));

      assertThat(underTest.retrieveSerializer(ProjectionWithBrokenSerializer.class))
          .isInstanceOf(SnapSerWithoutNoArgsConstructor.class);
    }

    @Test
    void alternativeSerializerWhenMultipleAreGiven() {
      assertThat(underTest.retrieveSerializer(ProjectionWithBrokenAndWorkingSerializer.class))
          .isInstanceOf(OtherSnapSer.class);
    }

    @Test
    void failIfSerializerCannotBeCreated() {
      assertThrows(
          SerializerInstantiationException.class,
          () -> underTest.retrieveSerializer(ProjectionWithBrokenSerializer.class));
    }

    @Test
    void failIfNoSerializersGiven() {
      assertThrows(
          SerializerInstantiationException.class,
          () -> underTest.retrieveSerializer(ProjectionWithNoSerializers.class));
    }
  }

  @SerializeUsing(SnapSerWithoutNoArgsConstructor.class)
  static class ProjectionWithBrokenSerializer implements SnapshotProjection {}

  @SerializeUsing({SnapSerWithoutNoArgsConstructor.class, OtherSnapSer.class})
  static class ProjectionWithBrokenAndWorkingSerializer implements SnapshotProjection {}

  @SerializeUsing(OtherSnapSer.class)
  static class ProjectionWithTwoSerializers implements SnapshotProjection {}

  @SerializeUsing({})
  static class ProjectionWithNoSerializers implements SnapshotProjection {}

  static class ProjectionWithDefaultSerializer implements SnapshotProjection {}
}
