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
package org.factcast.store.internal.pipeline;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Timer;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AutoFlushingServerPipelineTest {
  @Mock private ServerPipeline parent;
  @Mock private Supplier<Long> stopwatch;
  @Spy private Timer timer = new java.util.Timer();
  private AutoFlushingServerPipeline underTest;

  private static final long DELAY = AutoFlushingServerPipeline.AUTOFLUSH_CHECK_INTERVAL;

  @BeforeEach
  void setup() {
    // we use a long delay to avoid interference from the background timer if possible,
    // though we mostly test via manual calls to autoFlush() or by controlling the stopwatch.
    underTest = new AutoFlushingServerPipeline(parent, DELAY, stopwatch, timer);
  }

  @Test
  void testConstructorValidation() {
    assertThatThrownBy(() -> new AutoFlushingServerPipeline(parent, DELAY - 1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void testProcessDelegates() {
    Signal s = Signal.catchup();
    underTest.process(s);
    verify(parent).process(s);
  }

  @Nested
  class AutoFlush {
    @Test
    void testAutoFlushTriggersWhenDelayExceeded() {
      when(stopwatch.get()).thenReturn(0L, 5000L);

      // first flush to set lastFlush
      underTest.process(Signal.flush());
      verify(parent).process(any(Signal.FlushSignal.class));

      // now call autoFlush
      underTest.autoFlush();

      // should have triggered another flush
      verify(parent, times(2)).process(any(Signal.FlushSignal.class));
    }

    @Test
    void testAutoFlushDoesNotTriggerWhenDelayNotExceeded() {
      when(stopwatch.get()).thenReturn(1000L, 2000L);

      // first flush to set lastFlush
      underTest.process(Signal.flush());

      // now call autoFlush
      underTest.autoFlush();

      // should NOT have triggered another flush (only the first one)
      verify(parent, times(1)).process(any(Signal.FlushSignal.class));
    }

    @Test
    void testAutoFlushInitialTrigger() {
      // lastFlush is initially 0
      when(stopwatch.get()).thenReturn(DELAY + 1);

      underTest.autoFlush();

      // Verify that process was called with a Signal.FlushSignal
      ArgumentCaptor<Signal> captor = ArgumentCaptor.forClass(Signal.class);
      // It might have been called twice if the background timer triggered it too,
      // but in this test environment it's unlikely. However, let's just check AT LEAST once.
      verify(parent, atLeastOnce()).process(captor.capture());
      assertThat(captor.getAllValues()).anyMatch(s -> s instanceof Signal.FlushSignal);
    }
  }

  @Test
  void testClose() {
    underTest.close();
    verify(parent).close();
    verify(timer).cancel();
  }
}
