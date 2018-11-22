package org.factcast.core.spec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.factcast.core.MarkFact;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

// TODO remove?
public class FactSpecTest {

    @Test
    void testMarkMatcher() {
        assertTrue(new FactSpecMatcher(FactSpec.forMark()).test(new MarkFact()));
    }

    @Test
    void testMetaBothNull() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            FactSpec.ns("foo").meta(null, null);
        });
    }

    @Test
    void testMetaKeyNull() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            FactSpec.ns("foo").meta(null, "");
        });
    }

    @Test
    void testMetaValueNull() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            FactSpec.ns("foo").meta("", null);
        });
    }

    @Test
    void testFactSpecConstructorNull() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            new FactSpec(null);
        });
    }

    @SuppressWarnings("static-access")
    @Test
    void testFactSpecNs() {
        assertEquals("y", FactSpec.ns("x").ns("y").ns());
    }

    @Test
    void testFactSpecType() {
        assertEquals("y", FactSpec.ns("x").type("y").type());
    }

    @Test
    void testFactSpecAggId() {
        UUID id = UUID.randomUUID();
        assertEquals(id, FactSpec.ns("x").aggId(id).aggId());
    }

    @Test
    void testFactSpecJsFilter() {
        assertEquals("foo", FactSpec.ns("x").jsFilterScript("foo").jsFilterScript());
    }

    @Test
    void testFactSpecEquality() {
        FactSpec f1 = FactSpec.ns("x");
        FactSpec f2 = FactSpec.ns("x");
        // do not compare FactSpecs!
        assertNotEquals(f1, f2);
        assertNotSame(f1, f2);
    }
}
