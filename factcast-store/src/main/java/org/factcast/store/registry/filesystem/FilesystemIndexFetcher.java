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
package org.factcast.store.registry.filesystem;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Optional;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.factcast.store.registry.IndexFetcher;
import org.factcast.store.registry.RegistryIndex;
import org.factcast.store.registry.SchemaRegistryUnavailableException;
import org.factcast.store.registry.http.ValidationConstants;

@RequiredArgsConstructor
public class FilesystemIndexFetcher implements IndexFetcher {

  private final @NonNull String base;

  @Override
  public Optional<RegistryIndex> fetchIndex() {
    File file = new File(base, "index.json");
    return fetch_index(file);
  }

  @NonNull
  public static Optional<RegistryIndex> fetch_index(File file) {
    try {
      if (file.exists()) {
        return Optional.of(ValidationConstants.JACKSON.readValue(file, RegistryIndex.class));
      } else {
        throw new SchemaRegistryUnavailableException(
            new FileNotFoundException("Resource " + file + " does not exist."));
      }
    } catch (IOException e) {
      throw new SchemaRegistryUnavailableException(e);
    }
  }
}
