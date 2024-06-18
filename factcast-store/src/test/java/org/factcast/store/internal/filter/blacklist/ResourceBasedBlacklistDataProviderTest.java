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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Set;
import java.util.UUID;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

@ExtendWith(MockitoExtension.class)
class ResourceBasedBlacklistDataProviderTest {
  @Mock private Blacklist blacklist;
  @Mock private ResourceLoader resourceLoader;
  @Mock private BlacklistConfigurationProperties properties;
  @InjectMocks ResourceBasedBlacklistDataProvider underTest;

  private final String LOCATION = "blacklist.json";

  @Nested
  class WhenAfterSingletonsInstantiated {
    private final Resource resource = mock(Resource.class);

    @Test
    @SneakyThrows
    void fetches() {
      when(properties.getLocation()).thenReturn(LOCATION);
      when(resourceLoader.getResource("blacklist.json")).thenReturn(resource);
      when(resource.exists()).thenReturn(true);
      when(resource.getInputStream()).thenReturn(new ByteArrayInputStream("[]".getBytes()));

      underTest.afterSingletonsInstantiated();

      verify(blacklist).accept(any());
    }
  }

  @Nested
  class WhenReadingBlacklist {
    private final UUID blockedFactId1 = UUID.randomUUID();
    private final UUID blockedFactId2 = UUID.randomUUID();
    private final String blacklistContent =
        String.format(
            "[{\"id\":\"%s\", \"reason\":\"reason\"},{\"id\":\"%s\", \"reason\":\"otherReason\"}]",
            blockedFactId1, blockedFactId2);
    private final InputStream is = new ByteArrayInputStream(blacklistContent.getBytes());
    private final Resource resource = mock(Resource.class);

    @BeforeEach
    @SneakyThrows
    void setup() {
      when(properties.getLocation()).thenReturn(LOCATION);
      when(resourceLoader.getResource(LOCATION)).thenReturn(resource);
      when(resource.getInputStream()).thenReturn(is);
    }

    @Test
    void happyPath() {
      when(resource.exists()).thenReturn(true);

      Set<UUID> result = underTest.fetchBlacklist();

      assertThat(result).containsExactlyInAnyOrder(blockedFactId1, blockedFactId2);
    }

    @Test
    void throwsWhenBlacklistFileNotFound() {
      when(resource.exists()).thenReturn(false);

      assertThatThrownBy(() -> underTest.fetchBlacklist())
          .isInstanceOf(FileNotFoundException.class);
    }
  }
}
