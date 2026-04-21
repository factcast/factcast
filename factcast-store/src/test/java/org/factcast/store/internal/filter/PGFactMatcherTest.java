/*
 * Copyright © 2017-2025 factcast.org
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
package org.factcast.store.internal.filter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.assertj.core.api.Assertions;
import org.factcast.store.internal.PgFact;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PGFactMatcherTest {

  @Nested
  class WhenAnding {

    @Mock private PgFact f;
    @Mock private PGFactMatcher m1, m2, m3, m4;

    @Test
    void matchesAll() {

      when(m1.test(f)).thenReturn(true);
      when(m2.test(f)).thenReturn(true);
      when(m3.test(f)).thenReturn(true);
      when(m4.test(f)).thenReturn(true);

      PGFactMatcher.and(m1, m2, m3, m4).test(f);

      verify(m1).test(f);
      verify(m2).test(f);
      verify(m3).test(f);
      verify(m4).test(f);
    }

    @Test
    void skipsNull() {

      when(m1.test(f)).thenReturn(true);
      when(m2.test(f)).thenReturn(true);
      when(m3.test(f)).thenReturn(true);

      PGFactMatcher.and(m1, m2, null, m3).test(f);

      verify(m1).test(f);
      verify(m2).test(f);
      verify(m3).test(f);
    }

    @Test
    void empty() {
      Assertions.assertThat(PGFactMatcher.and().test(f)).isTrue();
    }
  }
}
