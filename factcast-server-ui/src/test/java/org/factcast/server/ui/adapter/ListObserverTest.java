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

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import lombok.NonNull;
import org.assertj.core.api.Assertions;
import org.factcast.core.Fact;
import org.factcast.core.FactHeader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ListObserverTest {

  private ListObserver underTest;

  @Nested
  class WhenOningNext {
    private final List<Fact> mockFacts = new LinkedList<>();

    @BeforeEach
    void setup() {
      for (int i = 0; i < 30; i++) {
        Fact mock = mock(Fact.class);
        lenient().when(mock.header()).thenReturn(mock(FactHeader.class));
        mockFacts.add(mock);
      }
    }

    @Test
    void limits() {
      underTest = new ListObserver(3, 2);
      assertThatThrownBy(() -> mockFacts.forEach(underTest::onNext))
          .isInstanceOf(LimitReachedException.class);

      var result = underTest.list();
      Assertions.assertThat(result).isNotNull().hasSize(3);
      Assertions.assertThat(underTest.isComplete()).isTrue();
    }

    @Test
    void lessThanLimit() {
      underTest = new ListObserver(50, 2);
      mockFacts.forEach(underTest::onNext);

      var result = underTest.list();
      Assertions.assertThat(result).isNotNull().hasSize(28);
      Assertions.assertThat(underTest.isComplete()).isFalse();
    }

    @Test
    void usesOffset() {
      underTest = new ListObserver(3, 2);
      assertThatThrownBy(() -> mockFacts.forEach(underTest::onNext))
          .isInstanceOf(LimitReachedException.class);

      var result = underTest.list();
      Assertions.assertThat(result)
          .isNotNull()
          .hasSize(3)
          .containsExactly(mockFacts.get(4), mockFacts.get(3), mockFacts.get(2));

      Assertions.assertThat(underTest.isComplete()).isTrue();
    }
  }

  @Nested
  class WhenCheckingIfIsComplete {
    @Test
    void switchesToComplete() {
      underTest = new ListObserver(2, 2);
      // first skipped for offset
      Fact mock = mock(Fact.class);
      when(mock.header()).thenReturn(mock(FactHeader.class));
      underTest.onNext(mock);
      assertThat(underTest.isComplete()).isFalse();
      // second skipped for offset
      underTest.onNext(mock);
      assertThat(underTest.isComplete()).isFalse();
      // third is taken, reduce limit to 1
      underTest.onNext(mock);
      assertThat(underTest.isComplete()).isFalse();
      // fourth is taken, reduce limit to 0
      underTest.onNext(mock);
      assertThat(underTest.isComplete()).isTrue();

      // more should trigger an exception
      assertThatThrownBy(
              () -> {
                underTest.onNext(mock);
              })
          .isInstanceOf(LimitReachedException.class);
    }

    @Nested
    class WhenFacingError {
      @Test
      void delegates() {
        underTest = spy(new ListObserver(2, 2));
        // first skipped for offset
        @NonNull Throwable exc = new IOException("expected - can be ignored");
        underTest.onError(exc);
        verify(underTest).handleError(exc);
      }

      @Test
      void swallowsLimitReached() {
        underTest = spy(new ListObserver(2, 2));
        // first skipped for offset
        @NonNull Throwable exc = new LimitReachedException();
        underTest.onError(exc);
        verify(underTest, never()).handleError(exc);
      }

      @Test
      void swallowsWrappedLimitReached() {
        underTest = spy(new ListObserver(2, 2));
        // first skipped for offset
        @NonNull
        Throwable exc = new RuntimeException(new RuntimeException(new LimitReachedException()));
        underTest.onError(exc);
        verify(underTest, never()).handleError(exc);
      }
    }
  }
}
