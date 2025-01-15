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
package org.factcast.factus.projection.parameter;

import java.util.Iterator;
import lombok.NonNull;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HandlerParameterContributorsTest {

  private HandlerParameterContributors underTest;

  @Nested
  class WhenIterating {
    @BeforeEach
    void setup() {
      underTest = new HandlerParameterContributors();
    }

    @Test
    void iteratesOverDefault() {
      Iterator<HandlerParameterContributor> i = underTest.iterator();
      Assertions.assertThat(i).hasNext();
      Assertions.assertThat(i.next()).isInstanceOf(DefaultHandlerParameterContributor.class);
      Assertions.assertThat(i.hasNext()).isFalse();
    }
  }

  @Nested
  class WhenWithingHighestPrio {
    @Mock private @NonNull HandlerParameterContributor topPrioContributor;

    @BeforeEach
    void setup() {
      underTest = new HandlerParameterContributors();
      underTest = underTest.withHighestPrio(topPrioContributor);
    }

    @Test
    void hasTopPrioFirst() {
      Iterator<HandlerParameterContributor> i = underTest.iterator();

      Assertions.assertThat(i).hasNext();
      Assertions.assertThat(i.next()).isSameAs(topPrioContributor);
      Assertions.assertThat(i.next()).isInstanceOf(DefaultHandlerParameterContributor.class);
      Assertions.assertThat(i.hasNext()).isFalse();
    }
  }
}
