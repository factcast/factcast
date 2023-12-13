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
package org.factcast.store.registry.classpath;

import java.util.Optional;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.factcast.store.registry.IndexFetcher;
import org.factcast.store.registry.RegistryIndex;
import org.springframework.core.io.ClassPathResource;

@RequiredArgsConstructor
public class ClasspathIndexFetcher implements IndexFetcher {

  private final @NonNull String base;

  private boolean initialRun = true;

  @Override
  public Optional<RegistryIndex> fetchIndex() {
    if (initialRun) {
      try {
        ClassPathResource file = new ClassPathResource(base + "/index.json");
        return RegistryIndex.fetch(file);
      } finally {
        initialRun = false;
      }
    } else
      // assume unchanged
      return Optional.empty();
  }
}
