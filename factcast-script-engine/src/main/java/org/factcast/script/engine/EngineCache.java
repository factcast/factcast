package org.factcast.script.engine;

import org.factcast.script.engine.exception.ScriptEngineException;

public interface EngineCache {

  Engine get(String script) throws ScriptEngineException;
}
