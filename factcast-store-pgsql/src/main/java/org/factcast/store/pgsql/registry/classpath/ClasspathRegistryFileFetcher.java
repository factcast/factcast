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
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.factcast.store.pgsql.registry.AbstractFileBasedRegistryFileFetcher;
import org.springframework.core.io.ClassPathResource;

@RequiredArgsConstructor
public class ClasspathRegistryFileFetcher extends AbstractFileBasedRegistryFileFetcher {

  private final @NonNull String base;

  @Override
  protected File getFile(String subPath) throws IOException {
    return new ClassPathResource(base + "/" + subPath).getFile();
  }
}
