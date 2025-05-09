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
package org.factcast.server.security.auth;

import static org.junit.jupiter.api.Assertions.*;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.*;

class AccessRulesTest {

  private final AccessRules uut = new AccessRules();

  @Test
  void testIncludesDefaultsToNull() {
    assertNull(uut.includes("foo"));
  }

  @Test
  void testIncludesNegative() {
    uut.exclude().add("foo");
    assertFalse(uut.includes("foo"));
  }

  @Test
  void testIncludesNegativeExcludeWins() {
    uut.exclude().add("foo");
    uut.include().add("foo");
    assertFalse(uut.includes("foo"));
  }

  @Test
  void testIncludesNegativeExcludeWildcardWins() {
    uut.exclude().add("*");
    uut.include().add("foo");
    assertFalse(uut.includes("foo"));
  }

  @Test
  void testIncludesPositive() {
    uut.include().add("foo");
    assertTrue(uut.includes("foo"));
  }

  @Test
  void testIncludesPositiveWildcardStar() {
    uut.include().add("*");
    assertTrue(uut.includes("foo"));
  }

  @Test
  void testIncludesPositiveWildcard() {
    uut.include().add("fo*");
    assertTrue(uut.includes("foo"));
  }

  @Test
  void testMissesPositiveWildcard() {
    uut.include().add("fo*");
    Assertions.assertThat(uut.includes("bar")).isNull();
  }
}
