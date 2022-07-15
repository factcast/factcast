/*
 * Copyright © 2017-2022 factcast.org
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
package org.factcast.store.internal.script.graaljs;

import java.util.*;
import java.util.function.*;

import javax.script.ScriptException;

import org.factcast.store.internal.script.JSArgument;
import org.factcast.store.internal.script.JSEngine;
import org.factcast.store.internal.script.exception.ScriptEngineException;
import org.factcast.store.registry.metrics.SupplierWithException;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.js.scriptengine.GraalJSScriptEngine;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GraalJSEngine implements JSEngine {

  private static final Object ENGINE_CREATION_MUTEX = new Object();

  private final GraalJSScriptEngine engine;

  GraalJSEngine(String script) throws ScriptEngineException {
    this.engine =
        withTruffleClassloader(
            () -> {
              try {
                // not stated anywhere that engine creation is thread-safe
                synchronized (ENGINE_CREATION_MUTEX) {
                  var graalJSScriptEngine =
                      GraalJSScriptEngine.create(null, NashornCompatContextBuilder.CTX);
                  graalJSScriptEngine.compile(script).eval();
                  return graalJSScriptEngine;
                }
              } catch (RuntimeException | ScriptException e) {
                log.debug("Exception during engine creation. Escalating.", e);
                throw new ScriptEngineException(e);
              }
            });
  }

  @Override
  public Object invoke(String functionName, JSArgument<?>... input) throws ScriptEngineException {
    try {
      synchronized (engine) {
        return withTruffleClassloader(
            () ->
                engine.invokeFunction(
                    functionName, Arrays.stream(input).map(Supplier::get).toArray()));
      }
    } catch (Exception e) {
      log.debug("Exception during the invocation of '{}'. Escalating.", functionName, e);
      throw new ScriptEngineException(e);
    }
  }

  public synchronized <T, E extends Exception> T withTruffleClassloader(
      SupplierWithException<T, E> block) {
    ClassLoader oldCl = Thread.currentThread().getContextClassLoader();
    Thread.currentThread().setContextClassLoader(Truffle.class.getClassLoader());
    try {
      // if you have transformations that add new objects like "foo.bar = {}"
      // this will lead to have PolyglotMap's being created inside jsonAsMap
      // these Graal maps are bound to a Context which bound to a ScriptEngine in our case
      // jackson seems somehow to access fields of this map which leads to exceptions like
      // mentioned in https://github.com/factcast/factcast/issues/1506
      return block.get();
    } catch (Exception e) {
      throw new ScriptEngineException(e);
    } finally {
      Thread.currentThread().setContextClassLoader(oldCl);
    }
  }
}
