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
package org.factcast.store.internal.pipeline;

import static org.mockito.Mockito.*;

import io.micrometer.core.instrument.Counter;
import lombok.NonNull;
import org.assertj.core.api.Assertions;
import org.factcast.core.Fact;
import org.factcast.core.subscription.SubscriptionRequest;
import org.factcast.store.internal.PgMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MetricServerPipelineTest {
  @Mock private @NonNull PgMetrics metrics;
  @Mock private @NonNull ServerPipeline parent;
  @InjectMocks private MetricServerPipeline underTest;

  @Nested
  class WhenProcessing {
    @Mock Fact fact;
    @Mock Counter count;
    @Mock SubscriptionRequest request;

    @BeforeEach
    void setup() {
      when(metrics.counter(any())).thenReturn(count);
      underTest = new MetricServerPipeline(parent, metrics, request);
    }

    @Test
    void delegatesAndCountsFact() {
      underTest.process(Signal.of(fact));
      verify(count).increment();
      ArgumentCaptor<Signal.FactSignal> cap = ArgumentCaptor.forClass(Signal.FactSignal.class);
      verify(parent).process(cap.capture());
      Assertions.assertThat(cap.getValue().fact()).isSameAs(fact);
    }

    @Test
    void delegatesNonFactSignal() {
      Signal signal = Signal.catchup();
      underTest.process(signal);
      verifyNoInteractions(count);
      ArgumentCaptor<Signal> cap = ArgumentCaptor.forClass(Signal.class);
      verify(parent).process(cap.capture());
      Assertions.assertThat(cap.getValue()).isSameAs(signal);
    }
  }
}
