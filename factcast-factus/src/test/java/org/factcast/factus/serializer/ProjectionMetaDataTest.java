package org.factcast.factus.serializer;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.*;

public class ProjectionMetaDataTest {
  @Test
  void testResolver() {
    assertThat(ProjectionMetaData.Resolver.resolveFor(With.class).get())
        .extracting(ProjectionMetaData::name, ProjectionMetaData::serial)
        .containsExactly("foo", 1L);

    assertThat(ProjectionMetaData.Resolver.resolveFor(Without.class)).isEmpty();
  }

  @ProjectionMetaData(name = "foo", serial = 1)
  static class With {}

  static class Without {}
}
