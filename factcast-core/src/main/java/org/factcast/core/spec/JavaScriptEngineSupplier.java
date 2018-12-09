/*
 * Copyright © 2018 Mercateo AG (http://www.mercateo.com)
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

import java.util.function.Supplier;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
class JavaScriptEngineSupplier implements Supplier<ScriptEngine> {
    static final ScriptEngineManager staticEngineManager = new ScriptEngineManager();

    final ScriptEngineManager manager;

    public JavaScriptEngineSupplier() {
        this(staticEngineManager);
    }

    ScriptEngine getEngineByName(String... names) {
        for (String name : names) {
            ScriptEngine engine = manager.getEngineByName(name);
            if (engine == null)
                log.error("'{}' engine unavailable.", name);
            else
                return engine;
        }
        return null;
    }

    @Override
    public ScriptEngine get() {
        ScriptEngine engine = getEngineByName("nashorn", "javascript", "js");
        if (engine == null)
            throw new IllegalStateException("Cannot find any engine to run javascript code.");
        return engine;
    }

}
