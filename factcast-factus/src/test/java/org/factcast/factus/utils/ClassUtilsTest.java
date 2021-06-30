package org.factcast.factus.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

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
