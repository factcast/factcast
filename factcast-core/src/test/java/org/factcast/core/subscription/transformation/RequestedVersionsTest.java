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
package org.factcast.core.subscription.transformation;

import java.util.*;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class RequestedVersionsTest {

  final RequestedVersions uut = new RequestedVersions();

  @Test
  void testEmpty() {
    Set<Integer> set = uut.get("foo", "bar");

    Assertions.assertThat(set).isNotNull().isEmpty();
  }

  @Test
  void testHappyPath() {
    uut.add("foo", "bar", 1);
    uut.add("foo", "bar", 2);
    Set<Integer> set = uut.get("foo", "bar");

    Assertions.assertThat(set).isNotEmpty().contains(1, 2).hasSize(2);
  }

  @Test
  void testHappyPathMulti() {
    uut.add("foo", "bar", 1);
    uut.add("foo", "baz", 2);
    Assertions.assertThat(uut.get("foo", "bar")).isNotEmpty().contains(1).hasSize(1);
    Assertions.assertThat(uut.get("foo", "baz")).isNotEmpty().contains(2).hasSize(1);
    Assertions.assertThat(uut.get("foo", "boo")).isEmpty();
  }

  @Test
  void testDontCare() {
    Assertions.assertThat(uut.matches("foo", "bar", 4)).isTrue();
  }

  @Test
  void testDontCare_byRequesting0() {
    uut.add("foo", "bar", 0);
    Assertions.assertThat(uut.matches("foo", "bar", 4)).isTrue();
  }

  @Test
  void testDontCare_negative() {
    uut.add("foo", "bar", 7);
    Assertions.assertThat(uut.matches("foo", "bar", 10)).isFalse();
  }

  @Test
  void testDontCare_byRequesting0NextToOthers() {
    uut.add("foo", "bar", 3);
    uut.add("foo", "bar", 0);
    uut.add("foo", "bar", 1);
    Assertions.assertThat(uut.matches("foo", "bar", 7)).isTrue();
  }

  @Test
  void testExactVersion() {
    uut.add("foo", "bar", 3);
    Assertions.assertThat(uut.matches("foo", "bar", 3)).isTrue();
    Assertions.assertThat(uut.matches("foo", "bar", 1)).isFalse();
  }

  @Test
  void testExactVersion_nextToOthers() {
    uut.add("foo", "bar", 3);
    uut.add("foo", "bar", 2);
    uut.add("foo", "bar", 1);
    Assertions.assertThat(uut.matches("foo", "bar", 3)).isTrue();
    Assertions.assertThat(uut.matches("foo", "bar", 1)).isTrue();
    Assertions.assertThat(uut.matches("foo", "bar", 5)).isFalse();
  }
}
