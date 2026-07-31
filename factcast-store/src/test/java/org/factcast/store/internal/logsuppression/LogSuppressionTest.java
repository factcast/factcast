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
package org.factcast.store.internal.logsuppression;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.core.spi.FilterReply;
import java.util.UUID;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.junit.jupiter.api.*;
import org.slf4j.MDC;

class LogSuppressionTest {

  @Nested
  class WithMinLevelDebugAndNoThreshold {
    DefaultLogSuppression uut = new DefaultLogSuppression(Level.DEBUG, 0, 0);

    @Nested
    class WhenSuppressionIsActive {

      @Test
      void deniesTrace() {
        try (var s = uut.forCatchup(new SubscriptionRequestTO())) {
          assertThat(uut.filter().decide(null, null, Level.TRACE, null, null, null))
              .isEqualTo(FilterReply.DENY);
        }
      }

      @Test
      void allowsDebug() {
        try (var s = uut.forCatchup(new SubscriptionRequestTO())) {
          assertThat(uut.filter().decide(null, null, Level.DEBUG, null, null, null))
              .isEqualTo(FilterReply.NEUTRAL);
        }
      }

      @Test
      void allowsInfo() {
        try (var s = uut.forCatchup(new SubscriptionRequestTO())) {
          assertThat(uut.filter().decide(null, null, Level.INFO, null, null, null))
              .isEqualTo(FilterReply.NEUTRAL);
        }
      }
    }

    @Nested
    class WhenMdcIsNotSet {
      DefaultLogSuppression uut = new DefaultLogSuppression(Level.INFO, 0, 0);

      @Test
      void allowsTrace() {
        assertThat(uut.filter().decide(null, null, Level.TRACE, null, null, null))
            .isEqualTo(FilterReply.NEUTRAL);
      }

      @Test
      void allowsDebug() {
        assertThat(uut.filter().decide(null, null, Level.DEBUG, null, null, null))
            .isEqualTo(FilterReply.NEUTRAL);
      }
    }
  }

  @Nested
  class WithMinLevelInfoAndNoThreshold {

    DefaultLogSuppression uut = new DefaultLogSuppression(Level.INFO, 0, 0);

    @Nested
    class WhenSuppressionIsActive {

      @Test
      void deniesTrace() {
        try (var s = uut.forCatchup(new SubscriptionRequestTO())) {
          assertThat(uut.filter().decide(null, null, Level.TRACE, null, null, null))
              .isEqualTo(FilterReply.DENY);
        }
      }

      @Test
      void deniesDebug() {
        try (var s = uut.forCatchup(new SubscriptionRequestTO())) {
          assertThat(uut.filter().decide(null, null, Level.DEBUG, null, null, null))
              .isEqualTo(FilterReply.DENY);
        }
      }

      @Test
      void allowsInfo() {
        try (var s = uut.forCatchup(new SubscriptionRequestTO())) {
          assertThat(uut.filter().decide(null, null, Level.INFO, null, null, null))
              .isEqualTo(FilterReply.NEUTRAL);
        }
      }

      @Test
      void allowsWarn() {
        try (var s = uut.forCatchup(new SubscriptionRequestTO())) {
          assertThat(uut.filter().decide(null, null, Level.WARN, null, null, null))
              .isEqualTo(FilterReply.NEUTRAL);
        }
      }
    }
  }

  @Nested
  class WithThreshold {

    DefaultLogSuppression uut = new DefaultLogSuppression(Level.DEBUG, 3, 0);

    @Test
    void allowsEventsUpToThreshold() {
      try (var s = uut.forCatchup(new SubscriptionRequestTO())) {

        // first 3 events should pass
        assertThat(uut.filter().decide(null, null, Level.TRACE, null, null, null))
            .isEqualTo(FilterReply.NEUTRAL);
        assertThat(uut.filter().decide(null, null, Level.TRACE, null, null, null))
            .isEqualTo(FilterReply.NEUTRAL);
        assertThat(uut.filter().decide(null, null, Level.TRACE, null, null, null))
            .isEqualTo(FilterReply.NEUTRAL);

        // 4th event should be denied
        assertThat(uut.filter().decide(null, null, Level.TRACE, null, null, null))
            .isEqualTo(FilterReply.DENY);
      }
    }

    @Test
    void eventsAtOrAboveMinLevelAreNotCounted() {
      try (var s = uut.forCatchup(new SubscriptionRequestTO())) {

        // DEBUG and above should always pass and not count toward threshold
        uut.filter().decide(null, null, Level.DEBUG, null, null, null);
        uut.filter().decide(null, null, Level.INFO, null, null, null);
        uut.filter().decide(null, null, Level.WARN, null, null, null);

        // TRACE events should still have full threshold budget
        assertThat(uut.filter().decide(null, null, Level.TRACE, null, null, null))
            .isEqualTo(FilterReply.NEUTRAL);
        assertThat(uut.filter().decide(null, null, Level.TRACE, null, null, null))
            .isEqualTo(FilterReply.NEUTRAL);
        assertThat(uut.filter().decide(null, null, Level.TRACE, null, null, null))
            .isEqualTo(FilterReply.NEUTRAL);
        assertThat(uut.filter().decide(null, null, Level.TRACE, null, null, null))
            .isEqualTo(FilterReply.DENY);
      }
    }
  }

  @Nested
  class WithSampling {

    // threshold=2, sampleRate=5: allow first 2 events, then every 5th
    final DefaultLogSuppression uut = new DefaultLogSuppression(Level.DEBUG, 2, 5);

    @Test
    void allowsEveryNthEventAfterThreshold() {
      try (var s = uut.forCatchup(new SubscriptionRequestTO())) {

        // events 1 and 2 pass (within threshold)
        assertThat(uut.filter().decide(null, null, Level.TRACE, null, null, null))
            .isEqualTo(FilterReply.NEUTRAL);
        assertThat(uut.filter().decide(null, null, Level.TRACE, null, null, null))
            .isEqualTo(FilterReply.NEUTRAL);

        // events 3-6 denied (past threshold, not on sampleRate boundary)
        for (int i = 3; i <= 6; i++) {
          assertThat(uut.filter().decide(null, null, Level.TRACE, null, null, null))
              .isEqualTo(FilterReply.DENY);
        }

        // event 7 allowed ((7 - 2) % 5 == 0)
        assertThat(uut.filter().decide(null, null, Level.TRACE, null, null, null))
            .isEqualTo(FilterReply.NEUTRAL);

        // events 8-11 denied
        for (int i = 8; i <= 11; i++) {
          assertThat(uut.filter().decide(null, null, Level.TRACE, null, null, null))
              .isEqualTo(FilterReply.DENY);
        }

        // event 12 allowed ((12 - 2) % 5 == 0)
        assertThat(uut.filter().decide(null, null, Level.TRACE, null, null, null))
            .isEqualTo(FilterReply.NEUTRAL);
      }
    }

    @Test
    void eventsAtOrAboveMinLevelAreUnaffectedBySampling() {
      try (var s = uut.forCatchup(new SubscriptionRequestTO())) {

        // exhaust threshold
        uut.filter().decide(null, null, Level.TRACE, null, null, null);
        uut.filter().decide(null, null, Level.TRACE, null, null, null);

        // DEBUG and above always pass regardless of sampling
        assertThat(uut.filter().decide(null, null, Level.DEBUG, null, null, null))
            .isEqualTo(FilterReply.NEUTRAL);
        assertThat(uut.filter().decide(null, null, Level.INFO, null, null, null))
            .isEqualTo(FilterReply.NEUTRAL);
      }
    }
  }

  @Nested
  class Lifecycle {
    final DefaultLogSuppression uut = new DefaultLogSuppression(Level.DEBUG, 2, 5);

    @Test
    void suppressionForSetsMdc() {
      try (var s = uut.forCatchup(new SubscriptionRequestTO())) {
        assertThat(MDC.get(DefaultLogSuppression.MDC_KEY)).isNotNull();
      }
    }

    @Test
    void suppressionForDoesNotSetMdcWhenNotFromScratch() {
      SubscriptionRequestTO req = new SubscriptionRequestTO();
      req.startingAfter(UUID.randomUUID());
      try (var s = uut.forCatchup(req)) {
        assertThat(MDC.get(DefaultLogSuppression.MDC_KEY)).isNull();
      }
    }

    @Test
    void closingSuppressionRemovesMdc() {
      try (var s = uut.forCatchup(new SubscriptionRequestTO())) {
        assertThat(MDC.get(DefaultLogSuppression.MDC_KEY)).isNotNull();
      }
      assertThat(MDC.get(DefaultLogSuppression.MDC_KEY)).isNull();
    }
  }
}
