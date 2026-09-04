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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PushbackServerPipelineTest {
  @Mock private ServerPipeline delegate;

  private PushbackServerPipeline underTest;

  @BeforeEach
  void setUp() {
    underTest = new PushbackServerPipeline(delegate);
  }

  @Test
  void testProcessDelegates() {
    Signal signal = Signal.catchup();
    underTest.process(signal);
    verify(delegate).process(signal);
  }

  @Test
  void testProcessThrowsWhenClosed() {
    Signal signal = Signal.catchup();
    underTest.close();
    assertThatThrownBy(() -> underTest.process(signal))
        .isInstanceOf(PipelineAlreadyClosedException.class);
    verify(delegate, never()).process(any());
  }

  @Test
  void testCloseDelegates() {
    underTest.close();
    verify(delegate).close();
  }

  @Test
  void testProcessNullSignalThrows() {
    assertThatThrownBy(() -> underTest.process(null)).isInstanceOf(NullPointerException.class);
  }
}
