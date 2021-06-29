package org.factcast.factus.projection;

import lombok.val;
import org.factcast.factus.serializer.ProjectionMetaData;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ScopedNameTest {

  @Test
  void forClass() {
    assertThatThrownBy(() -> ScopedName.forClass(MissingAnnotation.class))
        .isInstanceOf(IllegalStateException.class);
    assertThat(ScopedName.forClass(WithoutName.class).toString()).isEqualTo("WithoutName_2");
    assertThat(ScopedName.forClass(Complete.class).toString()).isEqualTo("hugo_3");
  }

  @Test
  void testWither() {
    val s = ScopedName.of("foo");
    val s2 = s.with("bar");

    assertThat(s).isNotSameAs(s2);
    assertThat(s.toString()).isEqualTo("foo");
    assertThat(s2.toString()).isEqualTo("foo_bar");
  }

  @ProjectionMetaData(serial = 2)
  static class WithoutName {}

  @ProjectionMetaData(name = "hugo", serial = 3)
  static class Complete {}

  static class MissingAnnotation {}
}
