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

import lombok.NonNull;
import org.factcast.core.Fact;
import org.factcast.store.internal.filter.blacklist.Blacklist;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BlacklistFilterFactPipelineTest {
  @Mock private @NonNull FactPipeline parent;

  @Mock Blacklist blacklist;
  @InjectMocks private BlacklistFilterFactPipeline underTest;

  @Nested
  class WhenFacting {
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private Fact fact;

    @BeforeEach
    void setup() {}

    @Test
    void filters() {
      when(blacklist.isBlocked(any())).thenReturn(true);
      underTest.fact(fact);
      verifyNoInteractions(parent);
    }

    @Test
    void delegates() {
      when(blacklist.isBlocked(any())).thenReturn(false);
      underTest.fact(fact);
      verify(parent).fact(fact);
    }

    @Test
    void delegatesNull() {
      underTest.fact(null);
      verifyNoInteractions(blacklist);
      verify(parent).fact(null);
    }
  }
}
