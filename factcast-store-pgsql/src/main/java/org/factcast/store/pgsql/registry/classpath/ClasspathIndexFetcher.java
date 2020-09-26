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

import java.io.File;
import java.io.IOException;
import java.util.Optional;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.factcast.store.pgsql.registry.IndexFetcher;
import org.factcast.store.pgsql.registry.RegistryIndex;
import org.factcast.store.pgsql.registry.SchemaRegistryUnavailableException;
import org.factcast.store.pgsql.registry.filesystem.FilesystemIndexFetcher;
import org.springframework.core.io.ClassPathResource;

@RequiredArgsConstructor
public class ClasspathIndexFetcher implements IndexFetcher {

  private final @NonNull String base;

  private boolean initialRun = true;

  @Override
  public Optional<RegistryIndex> fetchIndex() {
    if (initialRun) {

      try {
        File file = new ClassPathResource(base + "/index.json").getFile();
        return FilesystemIndexFetcher.fetch_index(file);

      } catch (IOException e) {
        throw new SchemaRegistryUnavailableException(e);
      } finally {
        initialRun = false;
      }

    } else
      // assume unchanged
      return Optional.empty();
  }
}
