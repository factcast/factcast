package org.factcast.core.spec;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.UUID;
import java.util.function.Predicate;

import org.factcast.core.Fact;
import org.factcast.core.Test0Fact;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class FactSpecMatcher0Test {

    @Test
    public void testMetaMatch() {
        assertTrue(metaMatch(FactSpec.ns("default").meta("foo", "bar"), new Test0Fact().meta("foo",
                "bar")));
        assertTrue(metaMatch(FactSpec.ns("default").meta("foo", "bar"), new Test0Fact().meta("x",
                "y").meta("foo", "bar")));
        assertTrue(metaMatch(FactSpec.ns("default"), new Test0Fact().meta("x", "y").meta("foo",
                "bar")));
        assertFalse(metaMatch(FactSpec.ns("default").meta("foo", "bar"), new Test0Fact().meta("foo",
                "baz")));
        assertFalse(metaMatch(FactSpec.ns("default").meta("foo", "bar"), new Test0Fact()));
    }

    @Test
    public void testNsMatch() {
        assertTrue(nsMatch(FactSpec.ns("default"), new Test0Fact().ns("default")));
        assertFalse(nsMatch(FactSpec.ns("default"), new Test0Fact().ns("xxx")));
    }

    @Test
    public void testTypeMatch() {
        assertTrue(typeMatch(FactSpec.ns("default").type("a"), new Test0Fact().type("a")));
        assertTrue(typeMatch(FactSpec.ns("default"), new Test0Fact().type("a")));
        assertFalse(typeMatch(FactSpec.ns("default").type("a"), new Test0Fact().type("x")));
        assertFalse(typeMatch(FactSpec.ns("default").type("a"), new Test0Fact()));
    }

    @Test
    public void testAggIdMatch() {
        UUID u1 = UUID.randomUUID();
        UUID u2 = UUID.randomUUID();
        assertTrue(aggIdMatch(FactSpec.ns("default").aggId(u1), new Test0Fact().aggId(u1)));
        assertTrue(aggIdMatch(FactSpec.ns("default"), new Test0Fact().aggId(u1)));
        assertFalse(aggIdMatch(FactSpec.ns("default").aggId(u1), new Test0Fact().aggId(u2)));
        assertFalse(aggIdMatch(FactSpec.ns("default").aggId(u1), new Test0Fact()));
    }

    @Test
    public void testScriptMatch() {
        assertTrue(scriptMatch(FactSpec.ns("default"), new Test0Fact()));
        assertFalse(scriptMatch(FactSpec.ns("default").jsFilterScript(
                "function (h,e){ return false }"), new Test0Fact()));
        assertTrue(scriptMatch(FactSpec.ns("default").jsFilterScript(
                "function (h,e){ return h.meta.x=='y' }"), new Test0Fact().meta("x", "y")));
    }

    // ---------------------------
    private boolean nsMatch(FactSpec s, Test0Fact f) {
        return new FactSpecMatcher(s).nsMatch(f);
    }

    private boolean typeMatch(FactSpec s, Test0Fact f) {
        return new FactSpecMatcher(s).typeMatch(f);
    }

    private boolean aggIdMatch(FactSpec s, Test0Fact f) {
        return new FactSpecMatcher(s).aggIdMatch(f);
    }

    private boolean scriptMatch(FactSpec s, Test0Fact f) {
        return new FactSpecMatcher(s).scriptMatch(f);
    }

    private boolean metaMatch(FactSpec s, Test0Fact f) {
        return new FactSpecMatcher(s).metaMatch(f);
    }

    @Test
    public void testMatchesAnyOfNull() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            FactSpecMatcher.matchesAnyOf(null);
        });
    }

    @Test
    public void testMatchesAnyOf() {
        Predicate<Fact> p = FactSpecMatcher.matchesAnyOf(Arrays.asList(FactSpec.ns("1"), FactSpec
                .ns("2")));
        assertTrue(p.test(new Test0Fact().ns("1")));
        assertTrue(p.test(new Test0Fact().ns("2")));
        assertFalse(p.test(new Test0Fact().ns("3")));
    }

    @Test
    public void testFactSpecMatcherNullConstructor() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            new FactSpecMatcher(null);
        });
    }
}
