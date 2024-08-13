/*
 * Copyright © 2017-2022 factcast.org
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
package org.factcast.factus.projection;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import nl.altindag.log.LogCaptor;
import nl.altindag.log.model.LogEvent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProgressAwareTest {

  private final ProgressAware underTest = new SomethingProgressAware();

  @Nested
  class WhenCatchingUpPercentage {

    @Test
    void logs() {
      LogCaptor logCaptor = LogCaptor.forClass(SomethingProgressAware.class);
      logCaptor.setLogLevelToDebug();

      List<LogEvent> logEvents = logCaptor.getLogEvents();
      assertThat(logEvents).isEmpty();

      underTest.catchupPercentage(81);

      logEvents = logCaptor.getLogEvents();
      assertThat(logEvents).hasSize(1);
      LogEvent logline = logEvents.get(0);

      assertThat(logline.getLoggerName()).contains(SomethingProgressAware.class.getSimpleName());
      assertThat(logline.getLevel()).isEqualTo("DEBUG");
      assertThat(logline.getFormattedMessage()).isEqualTo("catchup progress 81%");
    }
  }

  @Slf4j
  static class SomethingProgressAware implements ProgressAware {}
}
