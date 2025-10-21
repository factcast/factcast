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
package org.factcast.server.ui.adapter;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import lombok.NonNull;
import org.assertj.core.api.Assertions;
import org.factcast.core.Fact;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class UnlimitedConsumingObserverTest {

  private UnlimitedConsumingObserver underTest;

  @Nested
  class WhenOnNext {
    private final List<Fact> mockFacts = new LinkedList<>();

    @BeforeEach
    void setup() {
      for (int i = 0; i < 30; i++) {
        mockFacts.add(mock(Fact.class, Answers.RETURNS_DEEP_STUBS));
      }
    }

    @Test
    void usesOffset() {
      underTest = new UnlimitedConsumingObserver(27);
      assertThatCode(() -> mockFacts.forEach(underTest::onNext)).doesNotThrowAnyException();

      var result = underTest.list();
      Assertions.assertThat(result)
          .isNotNull()
          .hasSize(3)
          .containsExactly(mockFacts.get(29), mockFacts.get(28), mockFacts.get(27));
    }
  }

  @Nested
  class WhenFacingError {
    @Test
    void delegates() {
      underTest = spy(new UnlimitedConsumingObserver(2));
      // first skipped for offset
      @NonNull Throwable exc = new IOException("expected - can be ignored");
      underTest.onError(exc);
      verify(underTest).handleError(exc);
    }
  }

  @Nested
  class WhenCheckingIfIsComplete {
    @Test
    void switchesToComplete() {
      underTest = new UnlimitedConsumingObserver(3L, 2);
      // first skipped for offset
      Fact mock1 = mock(Fact.class, Answers.RETURNS_DEEP_STUBS);
      when(mock1.header().serial()).thenReturn(1L);
      Fact mock2 = mock(Fact.class, Answers.RETURNS_DEEP_STUBS);
      when(mock2.header().serial()).thenReturn(2L);
      Fact mock3 = mock(Fact.class, Answers.RETURNS_DEEP_STUBS);
      when(mock3.header().serial()).thenReturn(3L);
      Fact mock4 = mock(Fact.class, Answers.RETURNS_DEEP_STUBS);
      when(mock4.header().serial()).thenReturn(4L);

      // First one skipped for offset
      underTest.onNext(mock1);
      // second is taken, still under end serial
      underTest.onNext(mock2);
      // third is taken, equals end serial
      assertThat(underTest.isComplete(mock3)).isFalse();
      underTest.onNext(mock3);
      // fourth is above end serial
      assertThat(underTest.isComplete(mock4)).isTrue();

      // more should trigger an exception
      assertThatThrownBy(
              () -> {
                underTest.onNext(mock4);
              })
          .isInstanceOf(LimitReachedException.class);
    }
  }
}
