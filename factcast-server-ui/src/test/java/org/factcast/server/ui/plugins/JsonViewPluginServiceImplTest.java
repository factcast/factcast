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

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.assertj.core.api.Assertions;
import org.factcast.core.Fact;
import org.factcast.server.ui.metrics.UiMetrics;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JsonViewPluginServiceImplTest {

  @Mock private List<JsonViewPlugin> jsonViewPlugins;
  @Mock private ObjectMapper objectMapper;
  @Mock private JsonUtils jsonUtils;
  @Spy private UiMetrics uiMetrics = new UiMetrics.NOP();
  @InjectMocks private JsonViewPluginServiceImpl underTest;
  P1 p1 = spy(new P1());
  P2 p2 = spy(new P2());
  P3 p3 = spy(new P3());

  @Nested
  class WhenProcessing {

    @Test
    void timesProcessing() {
      Fact fact = Fact.builder().buildWithoutPayload();
      underTest =
          new JsonViewPluginServiceImpl(jsonViewPlugins, objectMapper, jsonUtils, uiMetrics) {
            @Override
            @NonNull
            JsonViewEntry processFact(@NonNull Fact fact) {
              return null;
            }
          };
      underTest.process(fact);
      verify(uiMetrics, times(1)).timeFactProcessing(any());
    }
  }

  @Nested
  class WhenGettingNonResponsivePlugins {
    @Test
    void filters() {

      underTest =
          new JsonViewPluginServiceImpl(List.of(p1, p2, p3), objectMapper, jsonUtils, uiMetrics);
      Assertions.assertThat(underTest.getNonResponsivePlugins())
          .hasSize(1)
          .containsExactlyInAnyOrder(p1.getDisplayName());
    }
  }

  @Nested
  class WhenProcessingFacts {
    @Mock private JsonPayload jsonPayload;

    @Test
    void callsOnlyReadyPlugins() {
      when(jsonUtils.forString(anyString())).thenReturn(jsonPayload);
      underTest =
          new JsonViewPluginServiceImpl(
              List.of(p1, p2, p3), new ObjectMapper(), jsonUtils, uiMetrics);

      var fact = Fact.builder().buildWithoutPayload();

      JsonViewEntry result = underTest.processFact(fact);

      verify(p1, never()).doHandle(same(fact), any(), any());
      verify(p2).doHandle(same(fact), any(), any());
      verify(p3).doHandle(same(fact), any(), any());
    }
  }

  @RequiredArgsConstructor
  class FakeJsonViewPlugin extends JsonViewPlugin {
    final boolean ready;

    @Override
    protected boolean isReady() {
      return ready;
    }

    @Override
    protected void doHandle(Fact fact, JsonPayload payload, JsonEntryMetaData jsonEntryMetaData) {}
  }

  class P1 extends FakeJsonViewPlugin {
    public P1() {
      super(false);
    }
  }

  class P2 extends FakeJsonViewPlugin {
    public P2() {
      super(true);
    }
  }

  class P3 extends FakeJsonViewPlugin {
    public P3() {
      super(true);
    }
  }
}
