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
package org.factcast.server.ui.plugins.bundled;

import static org.mockito.Mockito.*;

import java.time.ZoneId;
import java.util.TimeZone;
import org.assertj.core.api.Assertions;
import org.factcast.core.Fact;
import org.factcast.server.ui.plugins.JsonEntryMetaData;
import org.factcast.server.ui.plugins.JsonPayload;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HeaderMetaTimestampToDatePluginTest {

  @InjectMocks private HeaderMetaTimestampToDatePlugin underTest;

  @Nested
  class WhenDoingHandle {
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private Fact fact;

    @Mock private JsonPayload payload;
    @Mock private JsonEntryMetaData jsonEntryMetaData;

    @Test
    void skipOnNoTimestamp() {
      when(fact.header().timestamp()).thenReturn(null);
      underTest.doHandle(fact, payload, jsonEntryMetaData);

      verifyNoInteractions(jsonEntryMetaData);
    }

    @Test
    void addsTimestamp() {
      TimeZone oldDefault = TimeZone.getDefault();
      try {
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("Europe/Paris")));
        Long ts = 1001L;
        when(fact.header().timestamp()).thenReturn(ts);
        underTest.doHandle(fact, payload, jsonEntryMetaData);

        verify(jsonEntryMetaData)
            .annotateHeader("$.meta._ts", "1970-01-01T01:00:01.001+01:00[Europe/Paris]");
      } finally {
        TimeZone.setDefault(oldDefault);
      }
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
