/*
 * Copyright © 2017-2020 factcast.org
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
package org.factcast.core.spec;

import static org.assertj.core.api.Assertions.*;

import org.factcast.core.TestFact;
import org.factcast.factus.event.Specification;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FactSpecCoordinatesTest {

  @Specification(ns = "")
  @Nested
  class WhenFroming {
    @Test
    void failsIfNamespaceIsEmpty() {
      assertThatThrownBy(() -> FactSpecCoordinates.from(FactSpec.ns("")))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageStartingWith("Namespace must not be empty");
    }

    @Test
    void failsIfNamespaceIsEmptyInSpecification() {
      assertThatThrownBy(() -> FactSpecCoordinates.from(WhenFroming.class))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageStartingWith("Empty namespace encountered on class");
    }

    @Test
    void discoversFromFact() {
      FactSpecCoordinates res = FactSpecCoordinates.from(new SomeFact());
      assertThat(res.ns()).isEqualTo("some");
      assertThat(res.type()).isEqualTo("fact");
      assertThat(res.version()).isEqualTo(9);
    }
  }

  static class SomeFact extends TestFact {
    {
      ns("some");
      type("fact");
      version(9);
    }
  }
}
