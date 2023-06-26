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
package org.factcast.factus.projection;

import static org.assertj.core.api.Assertions.*;

import org.factcast.factus.serializer.ProjectionMetaData;
import org.junit.jupiter.api.*;

class ScopedNameTest {

  @Test
  void fromProjectionMetaData() {
    assertThatThrownBy(() -> ScopedName.fromProjectionMetaData(MissingAnnotation.class))
        .isInstanceOf(IllegalStateException.class);
    assertThat(ScopedName.fromProjectionMetaData(WithoutName.class).asString())
        .isEqualTo("org.factcast.factus.projection.ScopedNameTest$WithoutName_2");
    assertThat(ScopedName.fromProjectionMetaData(Complete.class).asString()).isEqualTo("hugo_3");
  }

  @Test
  void of() {
    assertThat(ScopedName.of("foo", 2)).extracting(ScopedName::asString).isEqualTo("foo_2");
  }

  @Test
  void asStringVsToString() {
    ScopedName n = ScopedName.of("foo", 2);

    assertThat(n.asString()).isEqualTo("foo_2");
    assertThat(n.toString()).isEqualTo("ScopedName(key=foo_2)");
  }

  @Test
  void testWither() {
    ScopedName s = ScopedName.of("foo");
    ScopedName s2 = s.with("bar");

    assertThat(s).isNotSameAs(s2);
    assertThat(s.asString()).isEqualTo("foo");
    assertThat(s2.asString()).isEqualTo("foo_bar");
  }

  @Test
  void testWitherRefusesEmpty() {
    ScopedName s = ScopedName.of("foo");
    assertThatThrownBy(
            () -> {
              s.with(" ");
            })
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ProjectionMetaData(projectionVersion = 2)
  static class WithoutName {}

  @ProjectionMetaData(name = "hugo", projectionVersion = 3)
  static class Complete {}

  static class MissingAnnotation {}
}
