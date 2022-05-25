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

import static java.util.Collections.*;
import static org.factcast.store.registry.transformation.chains.NashornCompatContextBuilder.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.js.scriptengine.GraalJSScriptEngine;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.script.Compilable;
import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.map.LRUMap;
import org.factcast.core.subscription.TransformationException;
import org.factcast.core.util.FactCastJson;
import org.factcast.store.registry.transformation.Transformation;

@Slf4j
public class GraalJsTransformer implements Transformer {

  private static final int ENGINE_CACHE_CAPACITY = 128;

  private static final Object ENGINE_MUTEX = new Object();
  private static final Map<String, Invocable> warmEngines =
      synchronizedMap(new LRUMap<>(ENGINE_CACHE_CAPACITY));

  static {
    // important property to enable nashorn compat mode within GraalJs
    // this is necessary for the way we currently do event transformation (in place modification of
    // event data)
    System.setProperty("polyglot.js.nashorn-compat", "true");

    // we ignore this because we're not running on graal and its somehow expected
    System.setProperty("polyglot.engine.WarnInterpreterOnly", "false");
  }

  private static final ExecutorService executorService =
      Executors.newWorkStealingPool((int) (Runtime.getRuntime().availableProcessors() / 1.5));

  @Override
  public JsonNode transform(Transformation t, JsonNode input) throws TransformationException {

    // trying to move the classloader-exchange busyness to another thread, so that i does not mess
    // with the current.
    if (t.transformationCode().isEmpty()) return input;
    else {
      String js = t.transformationCode().get();
      try {
        return CompletableFuture.supplyAsync(
                () -> {
                  // classloadershifting is necessary for truffle to find its peers
                  ClassLoader oldCl = Thread.currentThread().getContextClassLoader();
                  Thread.currentThread().setContextClassLoader(Truffle.class.getClassLoader());
                  try {
                    Invocable invocable =
                        warmEngines.computeIfAbsent(js, this::createAndWarmEngine);
                    return runJSTransformation(input, invocable);
                  } finally {
                    Thread.currentThread().setContextClassLoader(oldCl);
                  }
                },
                executorService)
            .get();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new TransformationException(e);
      } catch (ExecutionException e) {
        throw new TransformationException(e);
      }
    }
  }

  @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
  private JsonNode runJSTransformation(JsonNode input, Invocable invocable) {

    try {
      @SuppressWarnings("unchecked")
      Map<String, Object> jsonAsMap = FactCastJson.convertValue(input, Map.class);

      // if you have transformations that add new objects like "foo.bar = {}"
      // this will lead to have PolyglotMap's being created inside jsonAsMap
      // these Graal maps are bound to a Context which bound to a ScriptEngine in our case
      // jackson seems somehow to access fields of this map which leads to exceptions like
      // mentioned in https://github.com/factcast/factcast/issues/1506
      synchronized (invocable) {
        invocable.invokeFunction("transform", jsonAsMap);
        return FactCastJson.toJsonNode(jsonAsMap);
      }

    } catch (RuntimeException | ScriptException | NoSuchMethodException e) {
      // debug level, because it is escalated.
      log.debug("Exception during transformation. Escalating.", e);
      throw new TransformationException(e);
    }
  }

  @NonNull
  private Invocable createAndWarmEngine(String js) {
    ScriptEngine engine;

    synchronized (ENGINE_MUTEX) {
      // no guarantee is found anywhere, that creating a
      // scriptEngine was supposed to be threadsafe, so...
      engine = GraalJSScriptEngine.create(null, CTX);
    }

    try {
      Compilable compilable = (Compilable) engine;
      compilable.compile(js).eval();

      return (Invocable) engine;

    } catch (RuntimeException | ScriptException e) {
      // debug level, because it is escalated.
      log.debug("Exception during engine creation. Escalating.", e);
      throw new TransformationException(e);
    }
  }
}
