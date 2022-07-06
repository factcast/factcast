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
package org.factcast.core.spec;

import com.oracle.truffle.js.scriptengine.GraalJSEngineFactory;
import java.util.function.Supplier;
import javax.script.ScriptEngine;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Deprecated
@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
class JavaScriptEngineSupplier implements Supplier<ScriptEngine> {
  private static final GraalJSEngineFactory staticFactory = new GraalJSEngineFactory();

  final GraalJSEngineFactory factory;

  public JavaScriptEngineSupplier() {
    this(staticFactory);
  }

  @Override
  public ScriptEngine get() {
    ScriptEngine engine = factory.getScriptEngine();
    if (engine == null)
      throw new IllegalStateException("Cannot find any engine to run javascript code.");
    else return engine;
  }
}
