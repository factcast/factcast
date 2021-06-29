package org.factcast.factus.serializer;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class ProjectionMetaDataTest {
  @Test
  void testResolver() {
    assertThat(ProjectionMetaData.Resolver.resolveFor(With.class))
        .extracting(ProjectionMetaData::name, ProjectionMetaData::serial)
        .containsExactly("foo", 1L);

    assertThatThrownBy(() -> ProjectionMetaData.Resolver.resolveFor(Without.class))
        .isInstanceOf(IllegalStateException.class);
  }

  @ProjectionMetaData(name = "foo", serial = 1)
  static class With {}

  static class Without {}
}
