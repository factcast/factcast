/*
 * Copyright © 2017-2023 factcast.org
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

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.assertj.core.api.Assertions;
import org.factcast.factus.projection.SnapshotProjection;
import org.factcast.factus.serializer.SnapshotSerializer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SnapshotSerializerFactoryTest {

  @InjectMocks
  private SnapshotSerializerFactory underTest = new SnapshotSerializerFactory.Default();

  @Nested
  class WhenCreating {

    @Test
    void happyPath() {
      Assertions.assertThat(underTest.create(TestTypeInstanciable.class))
          .isNotNull()
          .isInstanceOf(TestTypeInstanciable.class);
    }

    @Test
    void failOnAbstract() {
      assertThatThrownBy(
              () -> {
                underTest.create(AbstractTestType.class);
              })
          .isInstanceOf(SerializerInstantiationException.class);
    }

    @Test
    void failOnNonDefaultConstructor() {
      assertThatThrownBy(
              () -> {
                underTest.create(TestTypeNotInstanciable.class);
              })
          .isInstanceOf(SerializerInstantiationException.class);
    }
  }

  abstract static class AbstractTestType implements SnapshotSerializer {}

  static class TestTypeNotInstanciable implements SnapshotSerializer {
    TestTypeNotInstanciable(SomeDependency s) {}

    @Override
    public byte[] serialize(SnapshotProjection a) {
      return new byte[0];
    }

    @Override
    public <A extends SnapshotProjection> A deserialize(Class<A> type, byte[] bytes) {
      return null;
    }

    @Override
    public boolean includesCompression() {
      return false;
    }

    @Override
    public String getId() {
      return null;
    }
  }

  static class TestTypeInstanciable implements SnapshotSerializer {
    @Override
    public byte[] serialize(SnapshotProjection a) {
      return new byte[0];
    }

    @Override
    public <A extends SnapshotProjection> A deserialize(Class<A> type, byte[] bytes) {
      return null;
    }

    @Override
    public boolean includesCompression() {
      return false;
    }

    @Override
    public String getId() {
      return null;
    }
  }

  class SomeDependency {}
}
