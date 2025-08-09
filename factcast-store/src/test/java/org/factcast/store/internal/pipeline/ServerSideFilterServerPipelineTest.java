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

import java.io.IOException;
import lombok.NonNull;
import org.factcast.core.FactStreamPosition;
import org.factcast.core.subscription.FactStreamInfo;
import org.factcast.store.internal.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ServerSideFilterServerPipelineTest {

  @Mock private @NonNull FactFilter matcher;
  @Mock private @NonNull ServerPipeline parent;
  private FilteringServerPipeline underTest;

  @Nested
  class WhenProcessing {

    @BeforeEach
    void setup() {
      underTest = new FilteringServerPipeline(parent, matcher);
    }

    @Test
    void passesCatchup() {
      assertPasses(Signal.catchup());
    }

    @Test
    void passesComplete() {
      assertPasses(Signal.complete());
    }

    @Test
    void passesFlush() {
      assertPasses(Signal.flush());
    }

    @Test
    void passesFfwd() {
      assertPasses(Signal.of(mock(FactStreamPosition.class)));
    }

    @Test
    void passesInfo() {
      assertPasses(Signal.of(mock(FactStreamInfo.class)));
    }

    @Test
    void passesError() {
      assertPasses(Signal.of(new IOException("buh")));
    }

    private void assertPasses(Signal s) {
      underTest.process(s);
      verify(parent).process(s);
    }

    @Test
    void passesFactWithDisabledMatcher() {
      when(matcher.canBeSkipped()).thenReturn(true);
      when(matcher.test(any())).thenCallRealMethod();
      assertPasses(Signal.of(mock(PgFact.class)));
    }

    @Test
    void filtersNonMatchingFact() {
      PgFact f = mock(PgFact.class);
      when(matcher.test(f)).thenReturn(true);
      assertPasses(Signal.of(f));
    }

    @Test
    void passesMatchingFact() {
      PgFact f = mock(PgFact.class);
      when(matcher.test(f)).thenReturn(false);
      underTest.process(Signal.of(f));
      verifyNoInteractions(parent);
    }
  }
}
