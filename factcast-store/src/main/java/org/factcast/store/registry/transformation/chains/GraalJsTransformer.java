/*
 * Copyright © 2017-2020 factcast.org
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
package org.factcast.store.registry.transformation.chains;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.subscription.TransformationException;
import org.factcast.core.util.FactCastJson;
import org.factcast.script.engine.Engine;
import org.factcast.script.engine.EngineCache;
import org.factcast.script.engine.exception.ScriptEngineException;
import org.factcast.script.engine.graaljs.GraalJSEngineCache;
import org.factcast.store.registry.transformation.Transformation;

@Slf4j
public class GraalJsTransformer implements Transformer {

  private static final EngineCache scriptEngineCache = new GraalJSEngineCache();

  @Override
  public JsonNode transform(Transformation t, JsonNode input) throws TransformationException {
    if (t.transformationCode().isEmpty()) {
      return input;
    } else {
      String js = t.transformationCode().get();
      return runJSTransformation(input, getEngine(js));
    }
  }

  private Engine getEngine(String js) {
    try {
      Engine cachedEngine = scriptEngineCache.get(js);
      return cachedEngine;
    } catch (ScriptEngineException e) {
      log.debug("Exception during engine creation. Escalating.", e);
      throw new TransformationException(e);
    }
  }

  @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
  private JsonNode runJSTransformation(JsonNode input, Engine engine) {
    try {
      @SuppressWarnings("unchecked")
      Map<String, Object> jsonAsMap = FactCastJson.convertValue(input, Map.class);
      synchronized (engine) {
        engine.invoke("transform", jsonAsMap);
        return FactCastJson.toJsonNode(jsonAsMap);
      }
    } catch (RuntimeException e) {
      // debug level, because it is escalated.
      log.debug("Exception during transformation. Escalating.", e);
      throw new TransformationException(e);
    }
  }
}
