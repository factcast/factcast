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

import java.util.Map;

import javax.script.Compilable;
import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.apache.commons.collections15.map.LRUMap;
import org.factcast.core.subscription.TransformationException;
import org.factcast.core.util.FactCastJson;
import org.factcast.store.pgsql.registry.transformation.Transformation;

import com.fasterxml.jackson.databind.JsonNode;

public class NashornTransformer implements Transformer {

    private static final int ENGINE_CACHE_CAPACITY = 128;

    private static final ScriptEngineManager scriptEngineManager = new ScriptEngineManager();

    private static final LRUMap<String, Invocable> warmEngines = new LRUMap<>(
            ENGINE_CACHE_CAPACITY);

    @Override
    public JsonNode transform(Transformation t, JsonNode input) throws TransformationException {

        if (!t.transformationCode().isPresent()) {
            return input;
        } else {
            String js = t.transformationCode().get();

            Invocable invocable = warmEngines.get(js);
            if (invocable == null) {
                ScriptEngine engine = null;
                synchronized (scriptEngineManager) {
                    // no guarantee is found anywhere, that creating a
                    // scriptEngine was
                    // supposed to be threadsafe, so...
                    engine = scriptEngineManager.getEngineByName("nashorn");
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
                return FactCastJson.toJsonNode(jsonAsMap);
            } catch (NoSuchMethodException | ScriptException e) {
                throw new TransformationException(e);
            }
        }
    }
}
