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
package org.factcast.store.internal;

import java.util.*;

import org.factcast.core.subscription.transformation.RequestedVersions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RequestedVersionsTest {

  final RequestedVersions uut = new RequestedVersions();

  @Test
  public void testEmpty() {
    Set<Integer> set = uut.get("foo", "bar");

    assertThat(set).isNotNull().isEmpty();
  }

  @Test
  public void testHappyPath() {
    uut.add("foo", "bar", 1);
    uut.add("foo", "bar", 2);
    Set<Integer> set = uut.get("foo", "bar");

    assertThat(set).isNotEmpty().contains(1, 2).hasSize(2);
  }

  @Test
  public void testHappyPathMulti() {
    uut.add("foo", "bar", 1);
    uut.add("foo", "baz", 2);
    assertThat(uut.get("foo", "bar")).isNotEmpty().contains(1).hasSize(1);
    assertThat(uut.get("foo", "baz")).isNotEmpty().contains(2).hasSize(1);
    assertThat(uut.get("foo", "boo")).isEmpty();
  }

  @Test
  public void testDontCare() {
    assertThat(uut.matches("foo", "bar", 4)).isTrue();
  }

  @Test
  public void testDontCare_byRequesting0() {
    uut.add("foo", "bar", 0);
    assertThat(uut.matches("foo", "bar", 4)).isTrue();
  }

  @Test
  public void testDontCare_negative() {
    uut.add("foo", "bar", 7);
    assertThat(uut.matches("foo", "bar", 10)).isFalse();
  }

  @Test
  public void testDontCare_byRequesting0NextToOthers() {
    uut.add("foo", "bar", 3);
    uut.add("foo", "bar", 0);
    uut.add("foo", "bar", 1);
    assertThat(uut.matches("foo", "bar", 7)).isTrue();
  }

  @Test
  public void testExactVersion() {
    uut.add("foo", "bar", 3);
    assertThat(uut.matches("foo", "bar", 3)).isTrue();
    assertThat(uut.matches("foo", "bar", 1)).isFalse();
  }

  @Test
  public void testExactVersion_nextToOthers() {
    uut.add("foo", "bar", 3);
    uut.add("foo", "bar", 2);
    uut.add("foo", "bar", 1);
    assertThat(uut.matches("foo", "bar", 3)).isTrue();
    assertThat(uut.matches("foo", "bar", 1)).isTrue();
    assertThat(uut.matches("foo", "bar", 5)).isFalse();
  }
}
