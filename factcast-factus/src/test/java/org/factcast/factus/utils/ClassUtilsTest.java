package org.factcast.factus.utils;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ClassUtilsTest {

  @Test
  void happyPath() {
    assertThat(ClassUtils.getNameFor(Foo.class)).isEqualTo("Foo");
    assertThat(ClassUtils.getNameFor(Bar.class)).isEqualTo("Bar");
  }

  @Test
  void filtersCgLib() {
    assertThat(ClassUtils.getNameFor(Foo$$EnhancerByCGLIB.class)).isEqualTo("Foo");
  }

  @Test
  void filtersSpring() {
    assertThat(ClassUtils.getNameFor(Foo$$EnhancerBySpring.class)).isEqualTo("Bar");
  }

  static class Foo {}

  static class Bar extends Foo {}

  static class Foo$$EnhancerByCGLIB extends Foo {}

  static class Foo$$EnhancerBySpring extends Bar {}
}
