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
package org.factcast.core.subscription;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FactStreamInfoTest {
  private static final long START_SERIAL = 1000;
  private static final long HORIZON_SERIAL = 2000;
  private FactStreamInfo underTest;

  @Nested
  class WhenCalculatingPercentage {
    @Test
    void percentage() {
      assertThat(new FactStreamInfo(START_SERIAL, HORIZON_SERIAL).calculatePercentage(1210))
          .isEqualTo(21);
    }

    @Test
    void handlesZeros() {
      assertThat(new FactStreamInfo(START_SERIAL, HORIZON_SERIAL).calculatePercentage(START_SERIAL))
          .isEqualTo(0);
      assertThat(new FactStreamInfo(0, 1).calculatePercentage(0)).isEqualTo(0);
      assertThat(new FactStreamInfo(100, 100).calculatePercentage(100)).isEqualTo(0);
    }
  }
}
