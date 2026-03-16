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
import com.google.common.annotations.VisibleForTesting;
import java.time.Duration;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.map.LRUMap;
import org.factcast.core.subscription.TransformationException;
import org.factcast.core.util.FactCastJson;
import org.factcast.store.internal.script.JSArgument;
import org.factcast.store.internal.script.JSEngine;
import org.factcast.store.internal.script.JSEngineFactory;
import org.factcast.store.internal.script.exception.ScriptEngineException;
import org.factcast.store.internal.script.graaljs.*;
import org.factcast.store.registry.transformation.Transformation;
import org.graalvm.polyglot.*;

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
  @VisibleForTesting
  protected JsonNode runJSTransformation(JsonNode input, String js) {
    try {
      JSEngine engine = getEngine(js);
      synchronized (engine) {
        final Map<String, Object> jsonAsMap = FactCastJson.convertValue(input, Map.class);
        final var jsArgument = JSArgument.byReference(jsonAsMap);
        engine.invoke("transform", jsArgument);
        return FactCastJson.toJsonNode(jsonAsMap);
      }
    } catch (RuntimeException e) {
      // debug level, because it is escalated.
      log.debug("Exception during transformation. Escalating.", e);
      throw new TransformationException(e);
    }
  }

  private static final Duration MIN_DELAY_UNTIL_CONTEXT_CLOSE = Duration.ofSeconds(20);
  private final Timer contextReaper = new Timer("ContextReaper", true);
  // maps script to Value within a given context assigned to the engine.
  private final Map<String, Value> VALUES =
      new LRUMap<String, Value>(128) {
        @Override
        protected boolean removeLRU(LinkEntry<String, Value> entry) {
          Context context = entry.getValue().getContext();
          // we're scheduling it to be cleaned up in 20 seconds, just to be sure we do not interfere
          // with a possibly running transformation
          contextReaper.schedule(
              new TimerTask() {
                @Override
                public void run() {
                  // maybe, we could check for the respective exception higher up the callstack and
                  // retry.
                  context.close(true);
                }
              },
              MIN_DELAY_UNTIL_CONTEXT_CLOSE.toMillis());
          return true;
        }
      };
  // using "the one" engine to make sure Source caching does happen.
  // this might deprecate org.factcast.store.internal.script.graaljs.GraalJSEngine
  private final Engine engine = Engine.create();

  protected JsonNode runJSTransformation_reuseContext(JsonNode input, String script) {
    Value v = VALUES.get(script);
    if (v == null) {
      Context ctx = NashornCompatContextBuilder.CTX.engine(engine).build();
      Source s = Source.create("js", script);
      // the parse call might be cheap due to Source caching
      Value e = ctx.parse(s);
      // this is used to get away without synchronization. If we created one to many, well, back
      // luck.
      VALUES.putIfAbsent(script, e);
    }

    final Map<String, Object> jsonAsMap = FactCastJson.convertValue(input, Map.class);
    final var jsArgument = JSArgument.byReference(jsonAsMap);
    v.execute("transform", jsonAsMap);

    return FactCastJson.toJsonNode(jsonAsMap);
  }

  protected JsonNode runJSTransformation_isolatedContext(JsonNode input, String script) {
    try (Context ctx = NashornCompatContextBuilder.CTX.engine(engine).build()) {
      Source s = Source.create("js", script);
      Value e = ctx.parse(s);
      final Map<String, Object> jsonAsMap = FactCastJson.convertValue(input, Map.class);
      final var jsArgument = JSArgument.byReference(jsonAsMap);
      e.execute("transform", jsonAsMap);

      return FactCastJson.toJsonNode(jsonAsMap);
    }
  }
}
