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
package org.factcast.store.internal.filter.blacklist;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BlacklistTest {

  private final Blacklist underTest = new Blacklist();

  @Nested
  class WhenCheckingIfIsBlocked {
    private final UUID FACT_ID = UUID.randomUUID();
    private final UUID NEW_FACT_ID = UUID.randomUUID();

    @BeforeEach
    void setup() {
      underTest.accept(Set.of(FACT_ID));
    }

    @Test
    void remembersBlocked() {
      assertThat(underTest.isBlocked(FACT_ID)).isTrue();
      assertThat(underTest.isBlocked(UUID.randomUUID())).isFalse();
    }

    @Test
    void updatesSet() {
      assertThat(underTest.isBlocked(FACT_ID)).isTrue();
      assertThat(underTest.isBlocked(NEW_FACT_ID)).isFalse();

      underTest.accept(Set.of(FACT_ID, NEW_FACT_ID));
      assertThat(underTest.isBlocked(FACT_ID)).isTrue();
      assertThat(underTest.isBlocked(NEW_FACT_ID)).isTrue();

      underTest.accept(Collections.emptySet());
      assertThat(underTest.isBlocked(FACT_ID)).isFalse();
      assertThat(underTest.isBlocked(NEW_FACT_ID)).isFalse();
    }
  }
}
