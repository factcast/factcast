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

import static java.util.Collections.*;

import java.util.*;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.map.LRUMap;
import org.factcast.store.internal.script.JSEngine;
import org.factcast.store.internal.script.JSEngineFactory;
import org.factcast.store.internal.script.exception.ScriptEngineException;

@Slf4j
public class GraalJSEngineFactory implements JSEngineFactory {

  private static final int ENGINE_CACHE_CAPACITY = 128;

  private static final Map<String, JSEngine> warmEngines =
      synchronizedMap(new LRUMap<>(ENGINE_CACHE_CAPACITY));

  @Override
  public JSEngine getOrCreateFor(String script) throws ScriptEngineException {
    return warmEngines.computeIfAbsent(script, this::createAndWarmEngine);
  }

  @NonNull
  private JSEngine createAndWarmEngine(String script) throws ScriptEngineException {
    return new GraalJSEngine(script);
  }
}
