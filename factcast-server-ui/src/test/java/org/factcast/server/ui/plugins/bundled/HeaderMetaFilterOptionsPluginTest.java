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

import static org.mockito.Mockito.*;

import java.util.List;
import org.assertj.core.api.Assertions;
import org.factcast.core.Fact;
import org.factcast.factus.event.MetaMap;
import org.factcast.server.ui.plugins.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HeaderMetaFilterOptionsPluginTest {

  @InjectMocks private HeaderMetaFilterOptionsPlugin underTest;

  @Nested
  class WhenDoingHandle {
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private Fact fact;

    @Mock private JsonPayload payload;
    @Mock private JsonEntryMetaData jsonEntryMetaData;

    @Test
    void addsMetaFilterOption() {
      when(fact.header().meta()).thenReturn(MetaMap.of("foo", "bar", "_ts", "123"));

      underTest.doHandle(fact, payload, jsonEntryMetaData);

      verify(jsonEntryMetaData).addHeaderMetaFilterOption("$.meta.foo", "foo", List.of("bar"));
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
