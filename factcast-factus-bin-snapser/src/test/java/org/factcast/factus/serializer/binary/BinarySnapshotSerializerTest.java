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
package org.factcast.factus.serializer.binary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.UUID;
import java.util.function.Function;
import org.factcast.factus.projection.SnapshotProjection;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BinarySnapshotSerializerTest {

  @InjectMocks private BinarySnapshotSerializer underTest;

  @Nested
  class WhenSerializing {

    @Test
    void canDeserialize() {
      TestProjection b =
          underTest.deserialize(TestProjection.class, underTest.serialize(new TestProjection()));
      assertEquals("bar", b.foo());
    }

    @Test
    void compresses() {
      CompressableTestProjection testProjection = new CompressableTestProjection();
      byte[] bytes = underTest.serialize(testProjection);
      CompressableTestProjection b = underTest.deserialize(CompressableTestProjection.class, bytes);
      assertEquals(b.someString(), testProjection.someString());
      // lots of same chars in there, should be able to compress to 50%
      // including the overhead of msgpack
      assertTrue(bytes.length < testProjection.someString().length() / 2);
    }
  }

  @Nested
  class whenCalculatingHash {
    @Test
    void calculateHash() {
      try {
        // In reality, an existing class would be changed. As we cannot
        // do this in a unit test (with easy means), we use several
        // classes instead to fake the lifecycle of the class.
        // The problem is that then, we will always get different hashes
        // as the Classname is part of the schema.
        // The solution is to rename the classname of all TestClassV*
        // classes to "TestClass".
        BinarySnapshotSerializer.schemaModifier(
            schema -> schema.replaceAll("TestClassV[^\"]*\"", "TestClass"));

        long v1 = underTest.calculateProjectionClassHash(TestClassV1.class);
        long v1a = underTest.calculateProjectionClassHash(TestClassV1a_noRelevantChange.class);
        long v2a = underTest.calculateProjectionClassHash(TestClassV2a_withChanges_newField.class);
        long v2b =
            underTest.calculateProjectionClassHash(TestClassV2b_withChanges_typeChanged.class);
        long v2c =
            underTest.calculateProjectionClassHash(TestClassV2c_withChanges_fieldRenamed.class);

        assertThat(v1).isEqualTo(v1a);

        assertThat(v1).isNotEqualTo(v2a);
        assertThat(v1).isNotEqualTo(v2b);
        assertThat(v1).isNotEqualTo(v2c);

      } finally {
        // reset to "do nothing"
        BinarySnapshotSerializer.schemaModifier(Function.identity());
      }
    }

    @Test
    void upcastingWorksWhenHashesAreEqual() {
      // INIT
      TestClassV1 testClassV1 = new TestClassV1();
      testClassV1.id = "123";
      testClassV1.x = 5;
      testClassV1.y = 9;

      // RUN
      byte[] serializedV1 = underTest.serialize(testClassV1);

      // ASSERT
      TestClassV1a_noRelevantChange deserializedV1a =
          underTest.deserialize(TestClassV1a_noRelevantChange.class, serializedV1);

      assertThat(deserializedV1a.id).isEqualTo(testClassV1.id);

      assertThat(deserializedV1a.x).isEqualTo(testClassV1.x);

      assertThat(deserializedV1a.y).isEqualTo(testClassV1.y);
    }

    @Test
    void downcastingWorksWhenHashesAreEqual() {
      // INIT
      TestClassV1a_noRelevantChange testClassV1a = new TestClassV1a_noRelevantChange();
      testClassV1a.id = "123";
      testClassV1a.x = 5;
      testClassV1a.y = 9;
      testClassV1a.ignoreMe = "xxx";

      // RUN
      byte[] serializedV1 = underTest.serialize(testClassV1a);

      // ASSERT
      TestClassV1 deserializedV1 = underTest.deserialize(TestClassV1.class, serializedV1);

      assertThat(deserializedV1.id).isEqualTo(testClassV1a.id);

      assertThat(deserializedV1.x).isEqualTo(testClassV1a.x);

      assertThat(deserializedV1.y).isEqualTo(testClassV1a.y);
    }
  }

  static class TestClassV1 implements SnapshotProjection {
    int x;

    int y;

    String id;
  }

  /** Order of items changed, ignored item added */
  static class TestClassV1a_noRelevantChange implements SnapshotProjection {
    int y;

    int x;

    String id;

    @JsonIgnore String ignoreMe;
  }

  static class TestClassV2a_withChanges_newField implements SnapshotProjection {
    int x;

    int y;

    String id;

    int i;
  }

  static class TestClassV2b_withChanges_typeChanged implements SnapshotProjection {
    int x;

    int y;

    UUID id;
  }

  static class TestClassV2c_withChanges_fieldRenamed implements SnapshotProjection {
    int x;

    int y;

    String userId;
  }
}
