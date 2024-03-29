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
package org.factcast.factus.serializer;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.*;

public class ProjectionMetaDataTest {
  @Test
  void testResolver() {
    assertThat(ProjectionMetaData.Resolver.resolveFor(WithSerial.class).get())
        .extracting(ProjectionMetaData::name, ProjectionMetaData.Revision::get)
        .containsExactly("foo", 1L);

    assertThat(ProjectionMetaData.Resolver.resolveFor(WithRevision.class).get())
        .extracting(ProjectionMetaData::name, ProjectionMetaData.Revision::get)
        .containsExactly("foo", 32L);

    assertThat(ProjectionMetaData.Resolver.resolveFor(WithBoth.class).get())
        .extracting(ProjectionMetaData::name, ProjectionMetaData.Revision::get)
        .containsExactly("foo", 33L);

    assertThat(ProjectionMetaData.Resolver.resolveFor(Without.class)).isEmpty();
  }

  @ProjectionMetaData(name = "foo", serial = 1)
  static class WithSerial {}

  @ProjectionMetaData(name = "foo", revision = 32)
  static class WithRevision {}

  @ProjectionMetaData(name = "foo", serial = 1, revision = 33)
  static class WithBoth {}

  static class Without {}
}
