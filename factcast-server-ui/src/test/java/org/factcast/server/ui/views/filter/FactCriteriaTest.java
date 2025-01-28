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
package org.factcast.server.ui.views.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.*;
import org.junit.jupiter.api.Test;

class FactCriteriaTest {

  @Test
  void alwaysHasSameHashCode() {
    FactCriteria fc1 =
        new FactCriteria("foo", Set.of("FooType"), UUID.randomUUID(), List.of(new MetaTuple()));
    FactCriteria fc2 =
        new FactCriteria("bar", Set.of("BarType"), UUID.randomUUID(), List.of(new MetaTuple()));
    assertThat(fc1).hasSameHashCodeAs(fc2);
  }

  @Test
  void neverEqualUnlessSame() {
    FactCriteria fc1 =
        new FactCriteria("foo", Set.of("FooType"), UUID.randomUUID(), List.of(new MetaTuple()));
    FactCriteria fc2 =
        new FactCriteria("foo", Set.of("FooType"), UUID.randomUUID(), List.of(new MetaTuple()));
    assertThat(fc1).isNotEqualTo(fc2).isEqualTo(fc1);
  }
}
