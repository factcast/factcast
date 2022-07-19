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

import java.util.*;

import org.factcast.core.subscription.TransformationException;
import org.factcast.core.util.FactCastJson;
import org.factcast.store.internal.script.JSArgument;
import org.factcast.store.internal.script.JSEngine;
import org.factcast.store.internal.script.JSEngineFactory;
import org.factcast.store.internal.script.exception.ScriptEngineException;
import org.factcast.store.registry.transformation.Transformation;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
public class JsTransformer implements Transformer {

  private final JSEngineFactory scriptEngineCache;

  @Override
  public JsonNode transform(Transformation t, JsonNode input) throws TransformationException {
    if (t.transformationCode().isEmpty()) {
      return input;
    } else {
      String js = t.transformationCode().get();
      return runJSTransformation(input, js);
    }
  }

  private JSEngine getEngine(String js) {
    try {
      return scriptEngineCache.getOrCreateFor(js);
    } catch (ScriptEngineException e) {
      log.debug("Exception during engine creation. Escalating.", e);
      throw new TransformationException(e);
    }
  }

  @SuppressWarnings("unchecked")
  private JsonNode runJSTransformation(JsonNode input, String js) {
    try {
      JSEngine engine = getEngine(js);
      synchronized (engine) {
        Map<String, Object> jsonAsMap = FactCastJson.convertValue(input, Map.class);
        engine.invoke("transform", JSArgument.byReference(jsonAsMap));
        return FactCastJson.toJsonNode(jsonAsMap);
      }
    } catch (RuntimeException e) {
      // debug level, because it is escalated.
      log.debug("Exception during transformation. Escalating.", e);
      throw new TransformationException(e);
    }
  }
}
