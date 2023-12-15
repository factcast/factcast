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
package org.factcast.server.ui.metrics;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import java.util.function.*;
import lombok.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MeterRegistryMetricsTest {
  @Mock private MeterRegistry meterRegistry;
  @InjectMocks private MeterRegistryMetrics underTest;

  @Nested
  class WhenTimingPluginExecution {
    private final String PLUGIN_DISPLAY_NAME = "PLUGIN_DISPLAY_NAME";
    @Mock private @NonNull Runnable r;

    @Test
    void runs() {
      underTest.timePluginExecution(PLUGIN_DISPLAY_NAME, r);
      verify(r, times(1)).run();
    }

    @Test
    void emitsTime() {
      underTest =
          spy(
              new MeterRegistryMetrics(meterRegistry) {
                @Override
                void time(
                    @NonNull Operations operation,
                    Timer.@NonNull Sample sample,
                    @NonNull Tags tags,
                    Exception e) {}
              });
      underTest.timePluginExecution(PLUGIN_DISPLAY_NAME, r);
      verify(underTest, times(1))
          .time(eq(Operations.PLUGIN_EXECUTION), any(), argThat(this::containsPluginName), any());
    }

    @Test
    void emitsTimeExceptional() {
      underTest =
          spy(
              new MeterRegistryMetrics(meterRegistry) {
                @Override
                void time(
                    @NonNull Operations operation,
                    Timer.@NonNull Sample sample,
                    @NonNull Tags tags,
                    Exception e) {}
              });
      Exception exception = new RuntimeException();
      doThrow(exception).when(r).run();
      assertThatThrownBy(
              () -> {
                underTest.timePluginExecution(PLUGIN_DISPLAY_NAME, r);
              })
          .isSameAs(exception);

      verify(underTest, times(1))
          .time(
              eq(Operations.PLUGIN_EXECUTION),
              any(),
              argThat(this::containsPluginName),
              same(exception));
    }

    private boolean containsPluginName(Tags tags) {
      return tags.stream().anyMatch(t -> t.getValue().equals(PLUGIN_DISPLAY_NAME));
    }
  }

  @Nested
  class WhenTimingFactProcessing {
    @Mock private @NonNull Supplier<?> s;

    @Test
    void runs() {
      underTest.timeFactProcessing(s);
      verify(s, times(1)).get();
    }

    @Test
    void emitsTime() {
      underTest =
          spy(
              new MeterRegistryMetrics(meterRegistry) {
                @Override
                void time(
                    @NonNull Operations operation,
                    Timer.@NonNull Sample sample,
                    @NonNull Tags tags,
                    Exception e) {}
              });
      underTest.timeFactProcessing(s);
      verify(underTest, times(1)).time(eq(Operations.FACT_PROCESSING), any(), any(), any());
    }

    @Test
    void emitsTimeExceptional() {
      underTest =
          spy(
              new MeterRegistryMetrics(meterRegistry) {
                @Override
                void time(
                    @NonNull Operations operation,
                    Timer.@NonNull Sample sample,
                    @NonNull Tags tags,
                    Exception e) {}
              });
      RuntimeException exception = new RuntimeException();
      when(s.get()).thenThrow(exception);
      assertThatThrownBy(
              () -> {
                underTest.timeFactProcessing(s);
              })
          .isSameAs(exception);
      verify(underTest, times(1))
          .time(eq(Operations.FACT_PROCESSING), any(), any(), same(exception));
    }
  }

  @Nested
  class WhenTiming {
    private final Operations OPERATION = Operations.PLUGIN_EXECUTION;
    @Mock private @NonNull Runnable r;

    @BeforeEach
    void setup() {}
  }

  //
  //  @Nested
  //  class WhenTiming {
  //    private final Operations OPERATION = Operations.PLUGIN_EXECUTION;
  //    @Mock private @NonNull Supplier<T> s;
  //
  //    @BeforeEach
  //    void setup() {}
  //  }

  @Nested
  class WhenAfteringPropertiesSet {
    @BeforeEach
    void setup() {}
  }
}
