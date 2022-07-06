package org.factcast.script.engine;

import org.factcast.script.engine.exception.ScriptEngineException;

public interface Engine {

  Engine warm(String script) throws ScriptEngineException;

  Object invoke(String functionName, Object... input) throws ScriptEngineException;

  Object eval(String script) throws ScriptEngineException;
}
