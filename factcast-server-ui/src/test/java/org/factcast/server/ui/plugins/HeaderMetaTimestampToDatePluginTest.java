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

import org.assertj.core.api.Assertions;
import org.factcast.core.Fact;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HeaderMetaTimestampToDatePluginTest {

  @InjectMocks private HeaderMetaTimestampToDatePlugin underTest;

  @Nested
  class WhenDoingHandle {
    @Mock private Fact fact;
    @Mock private JsonPayload payload;
    @Mock private JsonEntryMetaData jsonEntryMetaData;

    @Test
    void skipOnNoTimestamp() {
      when(fact.timestamp()).thenReturn(null);
      underTest.doHandle(fact, payload, jsonEntryMetaData);

      verifyNoInteractions(jsonEntryMetaData);
    }

    @Test
    void addsTimestamp() {
      Long ts = 1001L;
      when(fact.timestamp()).thenReturn(ts);
      underTest.doHandle(fact, payload, jsonEntryMetaData);

      verify(jsonEntryMetaData)
          .annotateHeader(eq("$.meta._ts"), eq("1970-01-01T01:00:01.001+01:00[Europe/Berlin]"));
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
