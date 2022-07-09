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
package org.factcast.script.engine.graaljs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.factcast.script.engine.Engine;
import org.factcast.script.engine.exception.ScriptEngineException;
import org.junit.jupiter.api.Test;

class GraalJSEngineCacheTest {

  private GraalJSEngineCache uut = new GraalJSEngineCache();

  @Test
  void testGet_createsNewEngineSuccessfully() {
    String validScript = "var a = 1";

    Engine e = uut.getOrCreateFor(validScript);
  }

  @Test
  void testGet_throwsExceptionUsingBadScript() {
    String badScript = "function test() { go away! }";

    assertThatThrownBy(
            () -> {
              uut.getOrCreateFor(badScript);
            })
        .isInstanceOf(ScriptEngineException.class);
  }

  @Test
  void testGet_returnsSameEngineFromCache() {
    String validScript = "1 == 1";

    Engine e1 = uut.getOrCreateFor(validScript);
    Engine e2 = uut.getOrCreateFor(validScript);

    assertThat(e1 == e2);
  }

  @Test
  void testGet_returnsDifferentEngineFromCache() {
    String s1 = "1 == 1";
    String s2 = "1 == 2";

    Engine e1 = uut.getOrCreateFor(s1);
    Engine e2 = uut.getOrCreateFor(s2);

    assertThat(e1 != e2);
  }
}
