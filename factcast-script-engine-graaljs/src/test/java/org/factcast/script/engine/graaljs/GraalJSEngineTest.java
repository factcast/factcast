package org.factcast.script.engine.graaljs;

import java.util.Map;
import org.assertj.core.util.Maps;
import org.factcast.script.engine.exception.ScriptEngineException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GraalJSEngineTest {

  private GraalJSEngine uut = new GraalJSEngine();

  @Test
  void testWarm_successfullyUsingValidScript() {
    String validScript = "var a = 1";

    uut.warm(validScript);
  }

  @Test
  void testWarm_throwsExceptionUsingBadScript() {
    String badScript = "var a = ;";

    assertThatThrownBy(
            () -> {
              uut.warm(badScript);
            })
        .isInstanceOf(ScriptEngineException.class);
  }

  @Test
  void testInvoke_sideEffectsOnInput() {
    String function = "function test(a) { a.key = 'changed' }";
    Map<String, String> input = Maps.newHashMap("key", "value");
    uut.warm(function);

    uut.invoke("test", input);

    assertThat(input.get("key").equals("changed"));
  }

  @Test
  void testInvoke_throwsExceptionOnInvalidFunctionName() {
    String function = "function test() { return 1 }";
    uut.warm(function);

    assertThatThrownBy(
            () -> {
              uut.invoke("invalidFunctionName");
            })
        .isInstanceOf(ScriptEngineException.class);
  }

  @Test
  void testEval_returnsFromScript() {
    String function = "function test() { return 1==1 }";
    uut.warm(function);

    Object result = uut.eval("test()");

    assertThat(result).isInstanceOf(Boolean.class);
    assertThat(result).isEqualTo(true);
  }

  @Test
  void testEval_throwsExceptionWhenCodeEvaluationShouldFail() {
    String function = "function test() { 1 == 1 }";
    uut.warm(function);

    assertThatThrownBy(
            () -> {
              uut.eval("test() should fail");
            })
        .isInstanceOf(ScriptEngineException.class);
  }
}
