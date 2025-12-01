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

import lombok.NonNull;
import org.assertj.core.api.Assertions;
import org.factcast.store.internal.PgFact;
import org.factcast.store.internal.filter.blacklist.Blacklist;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BlacklistFilterServerPipelineTest {
  @Mock private @NonNull ServerPipeline parent;

  @Mock Blacklist blacklist;
  @InjectMocks private BlacklistFilterServerPipeline underTest;

  @Nested
  class WhenFacting {
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private PgFact fact;

    @BeforeEach
    void setup() {}

    @Test
    void filters() {
      when(blacklist.isBlocked(any())).thenReturn(true);
      underTest.process(Signal.of(fact));
      verifyNoInteractions(parent);
    }

    @Test
    void delegates() {
      when(blacklist.isBlocked(any())).thenReturn(false);
      underTest.process(Signal.of(fact));
      ArgumentCaptor<Signal.FactSignal> cap = ArgumentCaptor.forClass(Signal.FactSignal.class);
      verify(parent).process(cap.capture());
      Assertions.assertThat(cap.getValue().fact()).isNotNull().isSameAs(fact);
    }

    @Test
    void delegatesNonFactSignal() {
      Signal signal = Signal.catchup();
      underTest.process(signal);
      verifyNoInteractions(blacklist);
      verify(parent).process(signal);
    }
  }
}
