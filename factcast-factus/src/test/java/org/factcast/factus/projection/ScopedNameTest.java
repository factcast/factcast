package org.factcast.factus.projection;

import static org.assertj.core.api.Assertions.*;

import lombok.val;
import org.factcast.factus.serializer.ProjectionMetaData;
import org.junit.jupiter.api.*;

class ScopedNameTest {

  @Test
  void fromProjectionMetaData() {
    assertThatThrownBy(() -> ScopedName.fromProjectionMetaData(MissingAnnotation.class))
        .isInstanceOf(IllegalStateException.class);
    assertThat(ScopedName.fromProjectionMetaData(WithoutName.class).toString())
        .isEqualTo("org.factcast.factus.projection.ScopedNameTest$WithoutName_2");
    assertThat(ScopedName.fromProjectionMetaData(Complete.class).toString()).isEqualTo("hugo_3");
  }

  @Test
  void of() {
    assertThat(ScopedName.of("foo", 2)).extracting(ScopedName::toString).isEqualTo("foo_2");
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
