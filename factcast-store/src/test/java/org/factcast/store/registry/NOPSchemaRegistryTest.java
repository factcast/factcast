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
package org.factcast.store.registry;

import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;
import org.factcast.store.registry.transformation.TransformationKey;
import org.factcast.store.registry.validation.schema.SchemaKey;
import org.junit.jupiter.api.*;

public class NOPSchemaRegistryTest {

  @Test
  void testSchemaGet() {
    NOPSchemaRegistry uut = new NOPSchemaRegistry();
    assertEquals(Optional.empty(), uut.get(SchemaKey.of("ns", "type", 1)));
  }

  @Test
  void testTransformationGet() {
    NOPSchemaRegistry uut = new NOPSchemaRegistry();
    assertTrue(uut.get(TransformationKey.of("ns", "type")).isEmpty());
  }

  @Test
  void testRefreshDoesNotThrow() {
    assertThatNoException()
        .isThrownBy(
            () -> {
              NOPSchemaRegistry uut = new NOPSchemaRegistry();
              uut.fetchInitial();
              uut.refresh();
            });
  }

  @Test
  void registerDoesNotThrow() {
    assertThatNoException()
        .isThrownBy(
            () -> {
              NOPSchemaRegistry uut = new NOPSchemaRegistry();
              uut.register(k -> {});
            });
  }

  @Test
  void invalidateNearCacheDoesNotThrow() {
    assertThatNoException()
        .isThrownBy(
            () -> {
              NOPSchemaRegistry uut = new NOPSchemaRegistry();
              uut.invalidateNearCache(SchemaKey.of("ns", "type", 1));
            });
  }

  @Test
  void enumerateNamespacesIsEmpty() {
    NOPSchemaRegistry uut = new NOPSchemaRegistry();
    assertThat(uut.enumerateNamespaces()).isEmpty();
  }

  @Test
  void enumerateTypesIsEmpty() {
    NOPSchemaRegistry uut = new NOPSchemaRegistry();
    assertThat(uut.enumerateTypes("ns")).isEmpty();
  }

  @Test
  void isNotActive() {
    assertThat(new NOPSchemaRegistry().isActive()).isFalse();
  }
}
