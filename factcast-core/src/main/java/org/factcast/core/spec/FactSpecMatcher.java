/**
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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

import org.factcast.core.Fact;

import com.fasterxml.jackson.databind.util.LRUMap;

import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

/**
 * Matches facts against specifications.
 *
 * @author uwe.schaefer@mercateo.com
 */
@Slf4j
public final class FactSpecMatcher implements Predicate<Fact> {

    static final ScriptEngineManager engineManager = new ScriptEngineManager();

    static final LRUMap<String, ScriptEngine> scriptEngineCache = new LRUMap<>(10, 200);

    @NonNull
    final String ns;

    final String type;

    final UUID aggId;

    final Map<String, String> meta;

    final String script;

    final ScriptEngine scriptEngine;

    public FactSpecMatcher(@NonNull FactSpec spec) {
        // opt: prevent method calls by prefetching to final fields.
        // yes, they might be inlined at some point, but making decisions based
        // on final fields should help.
        //
        // this Predicate is pretty performance critical
        ns = spec.ns();
        type = spec.type();
        aggId = spec.aggId();
        meta = spec.meta().isEmpty() ? null : spec.meta();
        script = spec.jsFilterScript();
        scriptEngine = getEngine(script);
    }

    public boolean test(Fact t) {
        boolean match = nsMatch(t);
        match = match && typeMatch(t);
        match = match && aggIdMatch(t);
        match = match && metaMatch(t);
        match = match && scriptMatch(t);
        return match;
    }

    protected boolean metaMatch(Fact t) {
        if ((meta == null) || meta.isEmpty()) {
            return true;
        }
        return meta.entrySet().parallelStream().allMatch(e -> e.getValue().equals(t.meta(e
                .getKey())));
    }

    protected boolean nsMatch(Fact t) {
        String otherNs = t.ns();
        return (ns.hashCode() == otherNs.hashCode()) && ns.equals(otherNs);
    }

    protected boolean typeMatch(Fact t) {
        if (type == null) {
            return true;
        }
        String otherType = t.type();
        return type.equals(otherType);
    }

    protected boolean aggIdMatch(Fact t) {
        if (aggId == null) {
            return true;
        }
        Set<UUID> otherAggId = t.aggIds();
        return otherAggId != null && otherAggId.contains(aggId);
    }

    @SneakyThrows
    protected boolean scriptMatch(Fact t) {
        if (script == null) {
            return true;
        }
        return (Boolean) scriptEngine.eval("test(" + t.jsonHeader() + "," + t.jsonPayload() + ")");
    }

    @SneakyThrows
    private static synchronized ScriptEngine getEngine(String js) {
        if (js == null) {
            return null;
        }
        ScriptEngine cachedEngine = scriptEngineCache.get(js);
        if (cachedEngine != null) {
            return cachedEngine;
        } else {
            ScriptEngine engine = getJavascriptEngine();
            engine.eval("var test=" + js);
            scriptEngineCache.put(js, engine);
            return engine;
        }
    }

    private static ScriptEngine getJavascriptEngine() {
        ScriptEngine engine = getEngineByName("nashorn", "javascript", "js");
        if (engine == null)
            throw new IllegalStateException("Cannot find any engine to run javascript code.");
        return engine;
    }

    private static ScriptEngine getEngineByName(String... names) {
        for (String name : names) {
            ScriptEngine engine = engineManager.getEngineByName(name);
            if (engine == null)
                log.error("'{}' engine unavailable.", name);
            else
                return engine;
        }
        return null;
    }

    public static Predicate<Fact> matchesAnyOf(@NonNull List<FactSpec> spec) {
        List<FactSpecMatcher> matchers = spec.stream().map(FactSpecMatcher::new).collect(Collectors
                .toList());
        return f -> matchers.stream().anyMatch(p -> p.test(f));
    }

    public static Predicate<Fact> matches(@NonNull FactSpec spec) {
        return new FactSpecMatcher(spec);
    }
}
