/*
 * Copyright © 2017-2024 factcast.org
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
package org.factcast.server.ui.plugins.bundled;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.jayway.jsonpath.spi.mapper.MappingException;
import java.util.List;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.factcast.core.Fact;
import org.factcast.server.ui.plugins.JsonEntryMetaData;
import org.factcast.server.ui.plugins.JsonPayload;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PayloadAggregateIdsFilterOptionsPluginTest {

  @InjectMocks private PayloadAggregateIdsFilterOptionsPlugin underTest;

  @Nested
  class WhenDoingHandle {
    @Mock private Fact fact;
    @Mock private JsonPayload payload;
    @Mock private JsonEntryMetaData jsonEntryMetaData;

    @Test
    void addsUuidFilterOption() {
      final var id = UUID.randomUUID();
      final var uuidPath = "uuid']";
      final var noUuidPath = "noUuid']";

      when(payload.findPaths("$..*")).thenReturn(List.of(uuidPath, noUuidPath));
      when(payload.read(uuidPath, UUID.class)).thenReturn(id);
      when(payload.read(noUuidPath, UUID.class))
          .thenThrow(new MappingException(new RuntimeException()));

      underTest.doHandle(fact, payload, jsonEntryMetaData);

      verify(jsonEntryMetaData).addPayloadAggregateIdFilterOption(uuidPath, id);
      verifyNoMoreInteractions(jsonEntryMetaData);
    }
  }

  @Nested
  class WhenCheckingIfIsReady {
    @Test
    void alwaysReady() {
      Assertions.assertThat(underTest.isReady()).isTrue();
    }
  }
}
