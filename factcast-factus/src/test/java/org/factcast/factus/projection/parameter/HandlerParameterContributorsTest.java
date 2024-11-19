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

import static org.mockito.Mockito.mock;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Iterator;
import java.util.Set;
import javax.annotation.Nullable;
import lombok.NonNull;
import org.assertj.core.api.Assertions;
import org.factcast.factus.event.EventSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// TODO find unit tests for parameter package

@ExtendWith(MockitoExtension.class)
class HandlerParameterContributorsTest {

  HandlerParameterContributor string =
      new HandlerParameterContributor() {
        @Nullable
        @Override
        public HandlerParameterProvider providerFor(
            @NonNull Class<?> type,
            @Nullable Type genericType,
            @NonNull Set<Annotation> annotations) {
          return (ser, f, p) -> "Hi There";
        }
      };
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
