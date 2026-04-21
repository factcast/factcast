/*
 * Copyright © 2017-2026 factcast.org
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
package org.factcast.store.internal.filter;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.core.spi.FilterReply;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class FromScratchCatchupTraceSuppressingTurboFilterTest {

  @AfterEach
  void cleanup() {
    MDC.clear();
  }

  @Nested
  class WithMinLevelDebug {

    final FromScratchCatchupLogSuppressingTurboFilter uut =
        new FromScratchCatchupLogSuppressingTurboFilter(Level.DEBUG);

    @Nested
    class WhenMdcIsSet {

      @Test
      void deniesTrace() {
        MDC.put(FromScratchCatchupLogSuppressingTurboFilter.MDC_KEY_FROM_SCRATCH, "true");

        assertThat(uut.decide(null, null, Level.TRACE, null, null, null))
            .isEqualTo(FilterReply.DENY);
      }

      @Test
      void allowsDebug() {
        MDC.put(FromScratchCatchupLogSuppressingTurboFilter.MDC_KEY_FROM_SCRATCH, "true");

        assertThat(uut.decide(null, null, Level.DEBUG, null, null, null))
            .isEqualTo(FilterReply.NEUTRAL);
      }

      @Test
      void allowsInfo() {
        MDC.put(FromScratchCatchupLogSuppressingTurboFilter.MDC_KEY_FROM_SCRATCH, "true");

        assertThat(uut.decide(null, null, Level.INFO, null, null, null))
            .isEqualTo(FilterReply.NEUTRAL);
      }
    }

    @Nested
    class WhenMdcIsNotSet {

      @Test
      void allowsTrace() {
        assertThat(uut.decide(null, null, Level.TRACE, null, null, null))
            .isEqualTo(FilterReply.NEUTRAL);
      }

      @Test
      void allowsDebug() {
        assertThat(uut.decide(null, null, Level.DEBUG, null, null, null))
            .isEqualTo(FilterReply.NEUTRAL);
      }
    }
  }

  @Nested
  class WithMinLevelInfo {

    final FromScratchCatchupLogSuppressingTurboFilter uut =
        new FromScratchCatchupLogSuppressingTurboFilter(Level.INFO);

    @Nested
    class WhenMdcIsSet {

      @Test
      void deniesTrace() {
        MDC.put(FromScratchCatchupLogSuppressingTurboFilter.MDC_KEY_FROM_SCRATCH, "true");

        assertThat(uut.decide(null, null, Level.TRACE, null, null, null))
            .isEqualTo(FilterReply.DENY);
      }

      @Test
      void deniesDebug() {
        MDC.put(FromScratchCatchupLogSuppressingTurboFilter.MDC_KEY_FROM_SCRATCH, "true");

        assertThat(uut.decide(null, null, Level.DEBUG, null, null, null))
            .isEqualTo(FilterReply.DENY);
      }

      @Test
      void allowsInfo() {
        MDC.put(FromScratchCatchupLogSuppressingTurboFilter.MDC_KEY_FROM_SCRATCH, "true");

        assertThat(uut.decide(null, null, Level.INFO, null, null, null))
            .isEqualTo(FilterReply.NEUTRAL);
      }

      @Test
      void allowsWarn() {
        MDC.put(FromScratchCatchupLogSuppressingTurboFilter.MDC_KEY_FROM_SCRATCH, "true");

        assertThat(uut.decide(null, null, Level.WARN, null, null, null))
            .isEqualTo(FilterReply.NEUTRAL);
      }
    }
  }
}
