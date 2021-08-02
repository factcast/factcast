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
package org.factcast.store.pgsql.registry.filesystem;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import org.factcast.store.pgsql.registry.SchemaRegistryUnavailableException;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;

class FilesystemIndexFetcherTest {

  @Test
  void fetchIndex() throws IOException {
    // INIT
    String path = new ClassPathResource("/example-registry/").getFile().getAbsolutePath();

    // RUN / ASSERT
    var uut = new FilesystemIndexFetcher(path);
    assertThat(uut.fetchIndex()).isPresent();
  }

  @Test
  void throwsOnNonExistingIndex() {
    assertThrows(
        SchemaRegistryUnavailableException.class,
        () -> {
          new FilesystemIndexFetcher("/not-there/").fetchIndex();
        });
  }
}
