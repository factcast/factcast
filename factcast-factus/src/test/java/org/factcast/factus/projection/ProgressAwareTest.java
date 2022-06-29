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

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import slf4jtest.*;

@ExtendWith(MockitoExtension.class)
class ProgressAwareTest {

  private final ProgressAware underTest = new SomethingProgressAware();

  private static final TestLoggerFactory lf = new TestLoggerFactory(new Settings().enableAll());

  @Nested
  class WhenCatchingUpPercentage {

    @Test
    void logs() {
      TestLogger logger = lf.getLogger(SomethingProgressAware.class);
      underTest.catchupPercentage(81);

      assertThat(logger.lines().size()).isEqualTo(1);
      LogMessage logline = logger.lines().stream().findFirst().get();
      assertThat(logline.logName).isEqualTo(logger.getName());
      assertThat(logline.level).isEqualTo(LogLevel.DebugLevel);
      assertThat(logline.text).isEqualTo("catchup progress 81%");
    }
  }

  @Slf4j
  static class SomethingProgressAware implements ProgressAware {
    @Override
    public Logger getLogger() {
      return lf.getLogger(SomethingProgressAware.class);
    }
  }
}
