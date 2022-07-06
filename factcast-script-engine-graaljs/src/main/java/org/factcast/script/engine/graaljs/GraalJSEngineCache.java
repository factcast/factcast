package org.factcast.script.engine.graaljs;

import java.util.Map;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.map.LRUMap;
import org.factcast.script.engine.Engine;
import org.factcast.script.engine.EngineCache;
import org.factcast.script.engine.exception.ScriptEngineException;

import static java.util.Collections.synchronizedMap;

@Slf4j
public class GraalJSEngineCache implements EngineCache {

  private static final int ENGINE_CACHE_CAPACITY = 128;

  private static final Map<String, Engine> warmEngines =
      synchronizedMap(new LRUMap<>(ENGINE_CACHE_CAPACITY));

  @Override
  public Engine get(String script) throws ScriptEngineException {
    return warmEngines.computeIfAbsent(script, this::createAndWarmEngine);
  }

  @NonNull
  private Engine createAndWarmEngine(String script) throws ScriptEngineException {
    return new GraalJSEngine().warm(script);
  }
}
