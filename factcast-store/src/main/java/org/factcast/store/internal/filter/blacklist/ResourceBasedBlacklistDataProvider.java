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
package org.factcast.store.internal.filter.blacklist;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

@Slf4j
@RequiredArgsConstructor
public final class ResourceBasedBlacklistDataProvider
    implements SmartInitializingSingleton, BlacklistDataProvider {

  private final ResourceLoader resourceLoader;
  private final BlacklistConfigurationProperties properties;
  private final Blacklist blacklist;

  @Override
  public void afterSingletonsInstantiated() {
    updateBlacklist();
  }

  private void updateBlacklist() {
    blacklist.accept(fetchBlacklist());
  }

  @SneakyThrows
  private Set<UUID> fetchBlacklist() {
    try (InputStream blacklistContent = readBlacklistFile(properties.getLocation())) {
      return parseBlacklist(blacklistContent).stream()
          .map(BlacklistEntry::id)
          .collect(Collectors.toSet());
    }
  }

  @SneakyThrows
  private List<BlacklistEntry> parseBlacklist(@NonNull InputStream stream) {
    ObjectMapper mapper = new ObjectMapper();
    return mapper.readValue(stream, new TypeReference<List<BlacklistEntry>>() {});
  }

  private InputStream readBlacklistFile(@NonNull String path) throws IOException {
    Resource resource = resourceLoader.getResource(path);
    if (!resource.exists()) {
      throw new FileNotFoundException(
          String.format("Blacklist could not be found at specified location: %s", path));
    }
    return resource.getInputStream();
  }
}
