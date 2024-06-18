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
package org.factcast.server.ui.plugins;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import lombok.NonNull;
import org.factcast.core.Fact;
import org.factcast.core.util.FactCastJson;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JsonViewPluginServiceTest {
  private JsonViewPluginService underTest =
      new JsonViewPluginService() {
        @Override
        public @NonNull JsonViewEntry process(@NonNull Fact fact) {
          return new JsonViewEntry(FactCastJson.newObjectNode(), new JsonEntryMetaData());
        }

        @Override
        public @NonNull Collection<String> getNonResponsivePlugins() {
          return null;
        }
      };

  @Nested
  class WhenProcessing {
    @Mock private @NonNull Fact fact;

    @Test
    void iterates() {
      underTest = spy(underTest);
      Fact f1 = Fact.builder().buildWithoutPayload();
      Fact f2 = Fact.builder().buildWithoutPayload();
      Fact f3 = Fact.builder().buildWithoutPayload();

      underTest.process(List.of(f1, f2, f3));

      verify(underTest, times(3)).process(any(Fact.class));
    }
  }
}
