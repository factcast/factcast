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
package org.factcast.script.engine.graaljs;

import static org.factcast.script.engine.graaljs.NashornCompatContextBuilder.CTX;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.js.scriptengine.GraalJSScriptEngine;
import java.util.Arrays;
import javax.script.ScriptException;
import lombok.extern.slf4j.Slf4j;
import org.factcast.script.engine.Argument;
import org.factcast.script.engine.Engine;
import org.factcast.script.engine.exception.ScriptEngineException;

@Slf4j
public class GraalJSEngine implements Engine {

  private static final Object ENGINE_CREATION_MUTEX = new Object();

  private GraalJSScriptEngine engine;

  GraalJSEngine(String script) throws ScriptEngineException {
    try {
      // not stated anywhere that engine creation is thread-safe
      synchronized (ENGINE_CREATION_MUTEX) {
        this.engine = GraalJSScriptEngine.create(null, CTX);
      }
      this.engine.compile(script).eval();
    } catch (RuntimeException | ScriptException e) {
      log.debug("Exception during engine creation. Escalating.", e);
      throw new ScriptEngineException(e);
    }
  }

  @Override
  public Object invoke(String functionName, Argument... input) throws ScriptEngineException {
    ClassLoader oldCl = Thread.currentThread().getContextClassLoader();
    Thread.currentThread().setContextClassLoader(Truffle.class.getClassLoader());
    try {
      // if you have transformations that add new objects like "foo.bar = {}"
      // this will lead to have PolyglotMap's being created inside jsonAsMap
      // these Graal maps are bound to a Context which bound to a ScriptEngine in our case
      // jackson seems somehow to access fields of this map which leads to exceptions like
      // mentioned in https://github.com/factcast/factcast/issues/1506
      synchronized (engine) {
        return engine.invokeFunction(
            functionName, Arrays.stream(input).map(i -> i.get()).toArray());
      }
    } catch (RuntimeException | ScriptException | NoSuchMethodException e) {
      log.debug("Exception during the invocation of '{}'. Escalating.", functionName, e);
      throw new ScriptEngineException(e);
    } finally {
      Thread.currentThread().setContextClassLoader(oldCl);
    }
  }
}
