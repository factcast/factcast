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

import lombok.NonNull;
import lombok.SneakyThrows;

/**
 * Matches facts against specifications.
 * 
 * @author uwe.schaefer@mercateo.com
 *
 */
public final class FactSpecMatcher implements Predicate<Fact> {

    static final ScriptEngineManager engineManager = new ScriptEngineManager();

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
        return !meta.entrySet().parallelStream().anyMatch(e -> !e.getValue().equals(t.meta(e
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
        Set<UUID> otherAggId = t.aggId();
        return otherAggId != null && otherAggId.contains(aggId);
    }

    @SneakyThrows
    protected boolean scriptMatch(Fact t) {
        if (script == null) {
            return true;
        }

        Boolean jsEval = (Boolean) scriptEngine.eval("test(" + t.jsonHeader() + "," + t
                .jsonPayload() + ")");
        return jsEval;
    }

    // TODO err handling
    @SneakyThrows
    private static ScriptEngine getEngine(String js) {
        if (js == null) {
            return null;
        }

        ScriptEngine engine = engineManager.getEngineByName("nashorn");
        engine.eval("var test=" + js);
        return engine;
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
