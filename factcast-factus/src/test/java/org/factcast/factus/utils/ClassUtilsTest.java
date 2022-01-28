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
package org.factcast.factus.utils;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ClassUtilsTest {

  @Test
  void happyPath() {
    assertThat(ClassUtils.getNameFor(Foo.class))
        .isEqualTo("org.factcast.factus.utils.ClassUtilsTest$Foo");
    assertThat(ClassUtils.getNameFor(Bar.class))
        .isEqualTo("org.factcast.factus.utils.ClassUtilsTest$Bar");
  }

  @Test
  void filtersCgLib() {
    assertThat(ClassUtils.getNameFor(Foo$$EnhancerByCGLIB.class))
        .isEqualTo("org.factcast.factus.utils.ClassUtilsTest$Foo");
  }

  @Test
  void filtersSpring() {
    assertThat(ClassUtils.getNameFor(Foo$$EnhancerBySpring.class))
        .isEqualTo("org.factcast.factus.utils.ClassUtilsTest$Bar");
  }

  static class Foo {}

  static class Bar extends Foo {}

  static class Foo$$EnhancerByCGLIB extends Foo {}

  static class Foo$$EnhancerBySpring extends Bar {}
}
