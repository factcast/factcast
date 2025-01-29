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

import org.junit.jupiter.api.Test;

class MetaTupleTest {

  @Test
  void alwaysHasSameHashCode() {
    final var mt1 = of("keyBar", "bar");
    final var mt2 = of("keyFoo", "foo");
    assertThat(mt1).hasSameHashCodeAs(mt2);
  }

  @Test
  void neverEqualUnlessSame() {
    final var mt1 = of("keyBar", "bar");
    final var mt2 = of("keyBar", "bar");
    assertThat(mt1).isNotEqualTo(mt2).isEqualTo(mt1);
  }

  static MetaTuple of(String key, String value) {
    final var result = new MetaTuple();
    result.setKey(key);
    result.setValue(value);
    return result;
  }
}
