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
package org.factcast.store.pgsql.registry.classpath;

import static org.junit.jupiter.api.Assertions.*;

import lombok.val;
import org.factcast.store.pgsql.registry.SchemaRegistryUnavailableException;
import org.junit.jupiter.api.*;

class ClasspathIndexFetcherTest {

  @Test
  void fetchIndex() {
    val uut = new ClasspathIndexFetcher("/example-registry/");
    val i = uut.fetchIndex();
  }

  @Test
  void doesNotFetchTwice() {

    val uut = new ClasspathIndexFetcher("/example-registry/");
    assertTrue(uut.fetchIndex().isPresent());
    assertFalse(uut.fetchIndex().isPresent());
    assertFalse(uut.fetchIndex().isPresent());
  }

  @Test
  void throwsOnNonExistingIndex() {
    assertThrows(
        SchemaRegistryUnavailableException.class,
        () -> {
          new ClasspathIndexFetcher("/not-there/").fetchIndex();
        });
  }
}
