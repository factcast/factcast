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
  class WithMinLevelDebugAndNoThreshold {

    final FromScratchCatchupLogSuppressingTurboFilter uut =
        new FromScratchCatchupLogSuppressingTurboFilter(Level.DEBUG, 0, 0);

    @Nested
    class WhenMdcIsSet {

      @Test
      void deniesTrace() {
        MDC.put(FromScratchCatchupLogSuppressingTurboFilter.MDC_KEY_FROM_SCRATCH, "catchup-1");

        assertThat(uut.decide(null, null, Level.TRACE, null, null, null))
            .isEqualTo(FilterReply.DENY);
      }

      @Test
      void allowsDebug() {
        MDC.put(FromScratchCatchupLogSuppressingTurboFilter.MDC_KEY_FROM_SCRATCH, "catchup-1");

        assertThat(uut.decide(null, null, Level.DEBUG, null, null, null))
            .isEqualTo(FilterReply.NEUTRAL);
      }

      @Test
      void allowsInfo() {
        MDC.put(FromScratchCatchupLogSuppressingTurboFilter.MDC_KEY_FROM_SCRATCH, "catchup-1");

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
  class WithMinLevelInfoAndNoThreshold {

    final FromScratchCatchupLogSuppressingTurboFilter uut =
        new FromScratchCatchupLogSuppressingTurboFilter(Level.INFO, 0, 0);

    @Nested
    class WhenMdcIsSet {

      @Test
      void deniesTrace() {
        MDC.put(FromScratchCatchupLogSuppressingTurboFilter.MDC_KEY_FROM_SCRATCH, "catchup-1");

        assertThat(uut.decide(null, null, Level.TRACE, null, null, null))
            .isEqualTo(FilterReply.DENY);
      }

      @Test
      void deniesDebug() {
        MDC.put(FromScratchCatchupLogSuppressingTurboFilter.MDC_KEY_FROM_SCRATCH, "catchup-1");

        assertThat(uut.decide(null, null, Level.DEBUG, null, null, null))
            .isEqualTo(FilterReply.DENY);
      }

      @Test
      void allowsInfo() {
        MDC.put(FromScratchCatchupLogSuppressingTurboFilter.MDC_KEY_FROM_SCRATCH, "catchup-1");

        assertThat(uut.decide(null, null, Level.INFO, null, null, null))
            .isEqualTo(FilterReply.NEUTRAL);
      }

      @Test
      void allowsWarn() {
        MDC.put(FromScratchCatchupLogSuppressingTurboFilter.MDC_KEY_FROM_SCRATCH, "catchup-1");

        assertThat(uut.decide(null, null, Level.WARN, null, null, null))
            .isEqualTo(FilterReply.NEUTRAL);
      }
    }
  }

  @Nested
  class WithThreshold {

    final FromScratchCatchupLogSuppressingTurboFilter uut =
        new FromScratchCatchupLogSuppressingTurboFilter(Level.DEBUG, 3, 0);

    @Test
    void allowsEventsUpToThreshold() {
      FromScratchCatchupLogSuppressingTurboFilter.beginCatchup("threshold-test");

      // first 3 events should pass
      assertThat(uut.decide(null, null, Level.TRACE, null, null, null))
          .isEqualTo(FilterReply.NEUTRAL);
      assertThat(uut.decide(null, null, Level.TRACE, null, null, null))
          .isEqualTo(FilterReply.NEUTRAL);
      assertThat(uut.decide(null, null, Level.TRACE, null, null, null))
          .isEqualTo(FilterReply.NEUTRAL);

      // 4th event should be denied
      assertThat(uut.decide(null, null, Level.TRACE, null, null, null))
          .isEqualTo(FilterReply.DENY);

      FromScratchCatchupLogSuppressingTurboFilter.endCatchup();
    }

    @Test
    void eventsAtOrAboveMinLevelAreNotCounted() {
      FromScratchCatchupLogSuppressingTurboFilter.beginCatchup("threshold-test-2");

      // DEBUG and above should always pass and not count toward threshold
      uut.decide(null, null, Level.DEBUG, null, null, null);
      uut.decide(null, null, Level.INFO, null, null, null);
      uut.decide(null, null, Level.WARN, null, null, null);

      // TRACE events should still have full threshold budget
      assertThat(uut.decide(null, null, Level.TRACE, null, null, null))
          .isEqualTo(FilterReply.NEUTRAL);
      assertThat(uut.decide(null, null, Level.TRACE, null, null, null))
          .isEqualTo(FilterReply.NEUTRAL);
      assertThat(uut.decide(null, null, Level.TRACE, null, null, null))
          .isEqualTo(FilterReply.NEUTRAL);
      assertThat(uut.decide(null, null, Level.TRACE, null, null, null))
          .isEqualTo(FilterReply.DENY);

      FromScratchCatchupLogSuppressingTurboFilter.endCatchup();
    }
  }

  @Nested
  class WithSampling {

    // threshold=2, sampleRate=5: allow first 2 events, then every 5th
    final FromScratchCatchupLogSuppressingTurboFilter uut =
        new FromScratchCatchupLogSuppressingTurboFilter(Level.DEBUG, 2, 5);

    @Test
    void allowsEveryNthEventAfterThreshold() {
      FromScratchCatchupLogSuppressingTurboFilter.beginCatchup("sample-test");

      // events 1 and 2 pass (within threshold)
      assertThat(uut.decide(null, null, Level.TRACE, null, null, null))
          .isEqualTo(FilterReply.NEUTRAL);
      assertThat(uut.decide(null, null, Level.TRACE, null, null, null))
          .isEqualTo(FilterReply.NEUTRAL);

      // events 3, 4 denied (past threshold, not on sampleRate boundary)
      assertThat(uut.decide(null, null, Level.TRACE, null, null, null))
          .isEqualTo(FilterReply.DENY);
      assertThat(uut.decide(null, null, Level.TRACE, null, null, null))
          .isEqualTo(FilterReply.DENY);

      // event 5 allowed (5 % 5 == 0)
      assertThat(uut.decide(null, null, Level.TRACE, null, null, null))
          .isEqualTo(FilterReply.NEUTRAL);

      // events 6-9 denied
      for (int i = 6; i <= 9; i++) {
        assertThat(uut.decide(null, null, Level.TRACE, null, null, null))
            .isEqualTo(FilterReply.DENY);
      }

      // event 10 allowed (10 % 5 == 0)
      assertThat(uut.decide(null, null, Level.TRACE, null, null, null))
          .isEqualTo(FilterReply.NEUTRAL);

      FromScratchCatchupLogSuppressingTurboFilter.endCatchup();
    }

    @Test
    void eventsAtOrAboveMinLevelAreUnaffectedBySampling() {
      FromScratchCatchupLogSuppressingTurboFilter.beginCatchup("sample-test-2");

      // exhaust threshold
      uut.decide(null, null, Level.TRACE, null, null, null);
      uut.decide(null, null, Level.TRACE, null, null, null);

      // DEBUG and above always pass regardless of sampling
      assertThat(uut.decide(null, null, Level.DEBUG, null, null, null))
          .isEqualTo(FilterReply.NEUTRAL);
      assertThat(uut.decide(null, null, Level.INFO, null, null, null))
          .isEqualTo(FilterReply.NEUTRAL);

      FromScratchCatchupLogSuppressingTurboFilter.endCatchup();
    }
  }

  @Nested
  class Lifecycle {

    @Test
    void beginCatchupSetsMdc() {
      FromScratchCatchupLogSuppressingTurboFilter.beginCatchup("lifecycle-test");

      assertThat(MDC.get(FromScratchCatchupLogSuppressingTurboFilter.MDC_KEY_FROM_SCRATCH))
          .isEqualTo("lifecycle-test");

      FromScratchCatchupLogSuppressingTurboFilter.endCatchup();
    }

    @Test
    void beginCatchupGeneratesFallbackIdWhenCatchupIdIsNull() {
      // null catchupId should generate a fallback UUID, not throw
      FromScratchCatchupLogSuppressingTurboFilter.beginCatchup(null);

      assertThat(MDC.get(FromScratchCatchupLogSuppressingTurboFilter.MDC_KEY_FROM_SCRATCH))
          .isNotNull();

      FromScratchCatchupLogSuppressingTurboFilter.endCatchup();
    }

    @Test
    void endCatchupRemovesMdcAndCounter() {
      FromScratchCatchupLogSuppressingTurboFilter.beginCatchup("cleanup-test");
      assertThat(MDC.get(FromScratchCatchupLogSuppressingTurboFilter.MDC_KEY_FROM_SCRATCH))
          .isNotNull();

      FromScratchCatchupLogSuppressingTurboFilter.endCatchup();
      assertThat(MDC.get(FromScratchCatchupLogSuppressingTurboFilter.MDC_KEY_FROM_SCRATCH))
          .isNull();
    }

    @Test
    void endCatchupIsNoOpWhenMdcNotSet() {
      // should not throw
      FromScratchCatchupLogSuppressingTurboFilter.endCatchup();
    }
  }
}
