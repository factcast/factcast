package org.factcast.script.engine.graaljs;

import org.factcast.script.engine.Engine;
import org.factcast.script.engine.exception.ScriptEngineException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GraalJSEngineCacheTest {

  private GraalJSEngineCache uut = new GraalJSEngineCache();

  @Test
  void testGet_createsNewEngineSuccessfully() {
    String validScript = "var a = 1";

    Engine e = uut.get(validScript);
  }

  @Test
  void testGet_throwsExceptionUsingBadScript() {
    String badScript = "function test() { go away! }";

    assertThatThrownBy(
            () -> {
              uut.get(badScript);
            })
        .isInstanceOf(ScriptEngineException.class);
  }

  @Test
  void testGet_returnsSameEngineFromCache() {
    String validScript = "1 == 1";

    Engine e1 = uut.get(validScript);
    Engine e2 = uut.get(validScript);

    assertThat(e1 == e2);
  }

  @Test
  void testGet_returnsDifferentEngineFromCache() {
    String s1 = "1 == 1";
    String s2 = "1 == 2";

    Engine e1 = uut.get(s1);
    Engine e2 = uut.get(s2);

    assertThat(e1 != e2);
  }
}
