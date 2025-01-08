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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class UnlimitedListObserverTest {

  private UnlimitedListObserver underTest;

  @Nested
  class WhenOningNext {
    private final List<Fact> mockFacts = new LinkedList<>();

    @BeforeEach
    void setup() {
      for (int i = 0; i < 30; i++) {
        mockFacts.add(mock(Fact.class));
      }
    }

    @Test
    void usesOffset() {
      underTest = new UnlimitedListObserver(27);
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
      underTest = spy(new UnlimitedListObserver(2));
      // first skipped for offset
      @NonNull Throwable exc = new IOException("expected - can be ignored");
      underTest.onError(exc);
      verify(underTest).handleError(exc);
    }
  }
}
