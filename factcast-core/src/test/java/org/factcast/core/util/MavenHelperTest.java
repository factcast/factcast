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
package org.factcast.core.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import java.util.*;
import org.junit.jupiter.api.Test;

class MavenHelperTest {
  @Test
  void testRetrieveImplementationVersion() {
    URL resource = MavenHelperTest.class.getResource("/test.properties");
    Optional<String> actual = MavenHelper.getImplVersion(resource);
    assertThat(actual).isPresent().hasValue("9.9.9");
  }

  @Test
  public void testRetrieveImplementationVersionEmptyPropertyFile() {
    URL resource = MavenHelperTest.class.getResource("/no-version.properties");
    Optional<String> actual = MavenHelper.getImplVersion(resource);
    assertThat(actual).isEmpty();
  }

  @Test
  public void testRetrieveImplementationVersionCannotReadFile() throws Exception {
    URL resource = MavenHelperTest.class.getResource("/file-not-found");
    Optional<String> actual = MavenHelper.getImplVersion(resource);
    assertThat(actual).isEmpty();
  }
}
