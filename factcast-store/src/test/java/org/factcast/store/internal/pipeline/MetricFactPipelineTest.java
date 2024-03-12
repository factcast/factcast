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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.micrometer.core.instrument.Counter;
import lombok.NonNull;
import org.factcast.core.Fact;
import org.factcast.store.internal.PgMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MetricFactPipelineTest {
  @Mock private @NonNull PgMetrics metrics;
  @Mock private @NonNull FactPipeline parent;
  @InjectMocks private MetricFactPipeline underTest;

  @Nested
  class WhenFacting {
    @Mock Fact fact;
    @Mock Counter count;

    @BeforeEach
    void setup() {
      when(metrics.counter(any())).thenReturn(count);
      underTest = new MetricFactPipeline(parent, metrics);
    }

    @Test
    void delegates() {
      underTest.fact(fact);
      verify(count).increment();
      verify(parent).fact(fact);
    }

    @Test
    void delegatesNull() {
      underTest.fact(null);
      verifyNoInteractions(count);
      verify(parent).fact(null);
    }
  }
}
