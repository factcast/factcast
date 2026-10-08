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
package org.factcast.factus.serializer.fury;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.*;
import testjson.*;

class FurySnapshotSerializerTest {

  static Root root;

  static {
    ObjectMapper om =
        new ObjectMapper().enable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION.mappedFeature());
    try {
      root = om.readValue(TestData.HUGE_JSON, Root.class);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private final FurySnapshotSerializer underTest = new FurySnapshotSerializer();
  private final FurySnapshotSerializer lz4UnderTest = new LZ4FurySnapshotSerializer();

  @Test
  void hasId() {
    Assertions.assertThat(underTest.id().name()).isEqualTo("fury");
    Assertions.assertThat(lz4UnderTest.id().name()).isEqualTo("lz4fury");
  }

  @Nested
  class WhenSerializingPlain {

    @Test
    void canDeserialize(TestInfo i) {

      root.kind = i.getDisplayName();
      System.out.println(i.getDisplayName());
      TestProjection source = new TestProjection().root(root);
      TestProjection source2 = new TestProjection().root(root);
      assertEquals(source.hashCode(), source2.hashCode());

      TestProjection target =
          underTest.deserialize(TestProjection.class, underTest.serialize(source));
      assertEquals("bar", target.foo());

      assertEquals(source.hashCode(), target.hashCode());
    }
  }

  @Nested
  class WhenSerializingLZ4 {

    @Test
    void canDeserialize(TestInfo i) {

      root.kind = i.getDisplayName();
      System.out.println(i.getDisplayName());
      TestProjection source = new TestProjection().root(root);
      TestProjection source2 = new TestProjection().root(root);
      assertEquals(source.hashCode(), source2.hashCode());

      TestProjection target =
          lz4UnderTest.deserialize(TestProjection.class, lz4UnderTest.serialize(source));
      assertEquals("bar", target.foo());

      assertEquals(source.hashCode(), target.hashCode());
    }

    @Test
    void compresses() {
      CompressableTestProjection testProjection = new CompressableTestProjection();
      byte[] bytes = lz4UnderTest.serialize(testProjection);
      CompressableTestProjection b =
          lz4UnderTest.deserialize(CompressableTestProjection.class, bytes);
      assertEquals(b.someString(), testProjection.someString());
      // lots of same chars in there, should be able to compress to 50%
      // including the overhead of msgpack
      assertTrue(bytes.length < (testProjection.someString().length() / 2));
    }
  }
}
