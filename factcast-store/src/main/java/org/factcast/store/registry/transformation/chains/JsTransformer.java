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

import com.google.common.cache.*;
import java.util.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.subscription.TransformationException;
import org.factcast.core.util.FactCastJson;
import org.factcast.store.internal.script.JsonString;
import org.factcast.store.internal.script.graaljs.NashornCompatContextBuilder;
import org.factcast.store.registry.transformation.Transformation;
import org.graalvm.polyglot.*;

@RequiredArgsConstructor
@Slf4j
public class JsTransformer implements Transformer {
  protected static final Engine engine = Engine.newBuilder("js").build();
  protected static final CacheLoader<String, Source> loader =
      CacheLoader.from(key -> Source.create("js", key));
  protected static final LoadingCache<String, Source> cache =
      CacheBuilder.newBuilder().softValues().build(loader);

  @SuppressWarnings("unchecked")
  private JsonString runJSTransformation(JsonString input, String js) {
    try {
      Source s = cache.get(js);
      final Map<String, Object> jsonAsMap = FactCastJson.readValue(Map.class, input.json());
      try (Context ctx = NashornCompatContextBuilder.CTX.engine(engine).build()) {
        ctx.eval(s).as(Fnc.class).run(jsonAsMap);
        return JsonString.of(FactCastJson.toJsonNode(jsonAsMap).toString());
      }

    } catch (Exception e) {
      // debug level, because it is escalated.
      log.debug("Exception during transformation. Escalating.", e);
      throw new TransformationException(e);
    }
  }

  interface Fnc {
    void run(Map<String, Object> arg);
  }

  @Override
  public JsonString transform(Transformation t, JsonString input) throws TransformationException {

    final var transformationCode = t.transformationCode();

    if (transformationCode.isEmpty()) {
      return input;
    } else {
      String script =
          "function (e) { var wrapped=" + transformationCode.get() + "; wrapped(e); return e; }";
      return runJSTransformation(input, script);
    }
  }
}
