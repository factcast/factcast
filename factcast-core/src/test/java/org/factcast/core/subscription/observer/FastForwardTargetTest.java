/*
 * Copyright © 2017-2022 factcast.org
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
package org.factcast.core.subscription.observer;

import static org.assertj.core.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FastForwardTargetTest {

  @Nested
  class WhenForingTest {
    @Test
    void createsAnyInstance() {
      assertThat(FastForwardTarget.forTest()).isNotNull();
    }
  }

  @Nested
  class WhenOfing {
    private final UUID ID = UUID.randomUUID();
    private final long SER = 67;

    @Test
    void passesValues() {
      assertThat(FastForwardTarget.of(ID, SER))
          .extracting(FastForwardTarget::targetId)
          .isEqualTo(ID);
      assertThat(FastForwardTarget.of(ID, SER))
          .extracting(FastForwardTarget::targetSer)
          .isEqualTo(SER);
    }
  }
}
