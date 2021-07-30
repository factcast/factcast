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
package org.factcast.store.pgsql.registry.transformation.chains;

import com.fasterxml.jackson.databind.JsonNode;
import com.oracle.truffle.js.scriptengine.GraalJSScriptEngine;
import lombok.val;
import org.apache.commons.collections4.map.LRUMap;
import org.factcast.core.subscription.TransformationException;
import org.factcast.core.util.FactCastJson;
import org.factcast.store.pgsql.registry.transformation.Transformation;
import org.graalvm.polyglot.Value;

import javax.script.Compilable;
import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import java.util.List;
import java.util.Map;

public class GraalJsTransformer implements Transformer {

  private static final int ENGINE_CACHE_CAPACITY = 128;

  private static final Object MUTEX = new Object();
  private static final LRUMap<String, Invocable> warmEngines = new LRUMap<>(ENGINE_CACHE_CAPACITY);

  static {
    // important property to enable nashorn compat mode within GraalJs
    // this is necessary for the way we currently do event transformation (in place modification of
    // event data)
    System.setProperty("polyglot.js.nashorn-compat", "true");

    // we ignore this because we're not running on graal and its somehow expected
    System.setProperty("polyglot.engine.WarnInterpreterOnly", "false");
  }

  @Override
  public JsonNode transform(Transformation t, JsonNode input) throws TransformationException {

    if (!t.transformationCode().isPresent()) {
      return input;
    } else {
      String js = t.transformationCode().get();

      Invocable invocable = warmEngines.get(js);
      if (invocable == null) {
        ScriptEngine engine = null;

        synchronized (MUTEX) {
          // no guarantee is found anywhere, that creating a
          // scriptEngine was
          // supposed to be threadsafe, so...
          engine = GraalJSScriptEngine.create(null, null);
        }

        try {
          Compilable compilable = (Compilable) engine;
          compilable.compile(js).eval();
          invocable = (Invocable) engine;
          warmEngines.put(js, invocable);
        } catch (ScriptException e) {
          throw new TransformationException(e);
        }
      }

      try {
        @SuppressWarnings("unchecked")
        Map<String, Object> jsonAsMap = FactCastJson.convertValue(input, Map.class);
        invocable.invokeFunction("transform", jsonAsMap);
        fixArrayTransformations(jsonAsMap);
        return FactCastJson.toJsonNode(jsonAsMap);
      } catch (NoSuchMethodException | ScriptException e) {
        throw new TransformationException(e);
      }
    }
  }

  void fixArrayTransformations(Map<String, Object> input) {
    // in order to keep memory footprint low and keep map order, replace in-place on demand
    for (String key : input.keySet()) {
      Object value = transformMapValue(input.get(key));
      input.put(key, value);
    }
  }

  private Object transformMapValue(Object input) {
    val value = Value.asValue(input);
    if (value.hasArrayElements()) {
      return value.as(List.class);
    } else if (input instanceof Map) {
      fixArrayTransformations((Map<String, Object>) input);
      return input;
    } else {
      return input;
    }
  }
}
