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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import java.util.Optional;
import java.util.UUID;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.store.LogSuppressionProperties;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

@ExtendWith(MockitoExtension.class)
class DefaultLogSuppressionTest {

  @Test
  void testConstructorValidation() {
    assertThatThrownBy(() -> new DefaultLogSuppression(Level.INFO, -1, 100))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new DefaultLogSuppression(Level.INFO, 100, -1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void testConstructorFromProperties() {
    LogSuppressionProperties props = new LogSuppressionProperties();
    props.setMinLogLevel(Level.DEBUG);
    props.setThreshold(500);
    props.setSampleRate(10);

    DefaultLogSuppression suppression = new DefaultLogSuppression(props);
    // Cannot easily check private fields, but we can check behavior via Filter
  }

  @Nested
  class ForCatchup {
    @Mock private SubscriptionRequestTO request;

    @Test
    void testForCatchup_FromScratch() {
      DefaultLogSuppression underTest = new DefaultLogSuppression(Level.INFO, 100, 100);
      when(request.startingAfter()).thenReturn(Optional.empty());

      LogSuppression.Suppression suppression = underTest.forCatchup(request);
      assertThat(suppression).isNotSameAs(LogSuppression.Suppression.NOP);
      assertThat(MDC.get(DefaultLogSuppression.MDC_KEY)).isNotNull();

      suppression.close();
      assertThat(MDC.get(DefaultLogSuppression.MDC_KEY)).isNull();
    }

    @Test
    void testForCatchup_NotFromScratch() {
      DefaultLogSuppression underTest = new DefaultLogSuppression(Level.INFO, 100, 100);
      when(request.startingAfter()).thenReturn(Optional.of(UUID.randomUUID()));

      LogSuppression.Suppression suppression = underTest.forCatchup(request);
      assertThat(suppression).isSameAs(LogSuppression.Suppression.NOP);
      assertThat(MDC.get(DefaultLogSuppression.MDC_KEY)).isNull();
    }
  }

  @Nested
  class FilterTest {
    private final DefaultLogSuppression suppression =
        new DefaultLogSuppression(Level.INFO, 2, 2); // Threshold 2, Sample 2
    private final DefaultLogSuppression.Filter filter = suppression.filter();
    private final Logger logger = mock(Logger.class);

    @Test
    void testDecide_NoMdc() {
      MDC.remove(DefaultLogSuppression.MDC_KEY);
      assertThat(filter.decide(null, logger, Level.DEBUG, null, null, null))
          .isEqualTo(FilterReply.NEUTRAL);
    }

    @Test
    void testDecide_HighLevel() {
      MDC.put(DefaultLogSuppression.MDC_KEY, "test");
      assertThat(filter.decide(null, logger, Level.ERROR, null, null, null))
          .isEqualTo(FilterReply.NEUTRAL);
    }

    @Test
    void testDecide_SuppressionLogic() {
      String id = "test-suppression-" + UUID.randomUUID();
      MDC.put(DefaultLogSuppression.MDC_KEY, id);

      // Set context to cover that branch
      ch.qos.logback.classic.LoggerContext ctx = mock(ch.qos.logback.classic.LoggerContext.class);
      when(ctx.getStatusManager()).thenReturn(mock(ch.qos.logback.core.status.StatusManager.class));
      filter.setContext(ctx);

      // We need to trigger create() to put it into counters map
      SubscriptionRequestTO req = mock(SubscriptionRequestTO.class);
      when(req.startingAfter()).thenReturn(Optional.empty());
      when(req.toString()).thenReturn(id);
      suppression.forCatchup(req);

      // Threshold is 2
      // 1st call: below threshold
      assertThat(filter.decide(null, logger, Level.DEBUG, null, null, null))
          .isEqualTo(FilterReply.NEUTRAL);
      // 2nd call: at threshold
      assertThat(filter.decide(null, logger, Level.DEBUG, null, null, null))
          .isEqualTo(FilterReply.NEUTRAL);

      // 3rd call: above threshold, sampleRate is 2. (3-2) % 2 != 0 -> DENY
      // Wait, (3-2) % 2 = 1.
      assertThat(filter.decide(null, logger, Level.DEBUG, null, null, null))
          .isEqualTo(FilterReply.DENY);

      // 4th call: above threshold, (4-2) % 2 == 0 -> NEUTRAL (sampled)
      assertThat(filter.decide(null, logger, Level.DEBUG, null, null, null))
          .isEqualTo(FilterReply.NEUTRAL);

      // 5th call: (5-2) % 2 = 1 -> DENY
      assertThat(filter.decide(null, logger, Level.DEBUG, null, null, null))
          .isEqualTo(FilterReply.DENY);

      // 6th call: (6-2) % 2 = 0 -> NEUTRAL (sampled)
      assertThat(filter.decide(null, logger, Level.DEBUG, null, null, null))
          .isEqualTo(FilterReply.NEUTRAL);
    }

    @Test
    void testDecide_NoSampling() {
      DefaultLogSuppression noSampling = new DefaultLogSuppression(Level.INFO, 1, 0);
      DefaultLogSuppression.Filter f = noSampling.filter();
      String id = "test-no-sampling";
      MDC.put(DefaultLogSuppression.MDC_KEY, id);

      SubscriptionRequestTO req = mock(SubscriptionRequestTO.class);
      when(req.startingAfter()).thenReturn(Optional.empty());
      when(req.toString()).thenReturn(id);
      noSampling.forCatchup(req);

      // 1st call: threshold
      assertThat(f.decide(null, logger, Level.DEBUG, null, null, null))
          .isEqualTo(FilterReply.NEUTRAL);
      // 2nd call: above threshold
      assertThat(f.decide(null, logger, Level.DEBUG, null, null, null)).isEqualTo(FilterReply.DENY);
      // 3rd call: still DENY
      assertThat(f.decide(null, logger, Level.DEBUG, null, null, null)).isEqualTo(FilterReply.DENY);
    }
  }

  @Test
  void testLifecycle() {
    DefaultLogSuppression underTest = new DefaultLogSuppression(Level.INFO, 100, 100);
    assertThatCode(underTest::start).doesNotThrowAnyException();
    assertThatCode(underTest::stop).doesNotThrowAnyException();
  }
}
