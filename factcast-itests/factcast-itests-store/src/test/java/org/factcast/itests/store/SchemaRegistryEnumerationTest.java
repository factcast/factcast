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
package org.factcast.itests.store;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import org.factcast.store.registry.SchemaRegistry;
import org.factcast.test.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@ExtendWith(MockitoExtension.class)
@SpringBootTest
@IntegrationTest
public class SchemaRegistryEnumerationTest {
  @Autowired SchemaRegistry schemaRegistry;

  @Test
  public void enumerateNamespacesAndTypes() {
    var ns = schemaRegistry.enumerateNamespaces();
    assertEquals(ns, Set.of("organisations", "users", "products"));
    var types = schemaRegistry.enumerateTypes("users");
    assertEquals(types, Set.of("UserUpdated", "UserCreated", "UserDeleted"));
  }

  @Test
  public void enumerateVersions() {
    var userVersions = schemaRegistry.enumerateVersions("users", "UserCreated");
    assertEquals(userVersions, Set.of(1, 2, 3));
    var types = schemaRegistry.enumerateVersions("users", "UserDeleted");
    assertEquals(types, Set.of(1));
  }
}
