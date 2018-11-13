package org.factcast.core.spec;

import static org.junit.Assert.*;

import java.util.UUID;

import org.factcast.core.MarkFact;
import org.junit.Test;

//TODO remove?
public class FactSpec0Test {

    @Test
    public void testMarkMatcher() {
        assertTrue(new FactSpecMatcher(FactSpec.forMark()).test(new MarkFact()));
    }

    @Test(expected = NullPointerException.class)
    public void testMetaBothNull() {
        FactSpec.ns("foo").meta(null, null);
    }

    @Test(expected = NullPointerException.class)
    public void testMetaKeyNull() {
        FactSpec.ns("foo").meta(null, "");
    }

    @Test(expected = NullPointerException.class)
    public void testMetaValueNull() {
        FactSpec.ns("foo").meta("", null);
    }

    @Test(expected = NullPointerException.class)
    public void testFactSpecConstructorNull() {
        new FactSpec(null);
    }

    @SuppressWarnings("static-access")
    @Test
    public void testFactSpecNs() {
        assertEquals("y", FactSpec.ns("x").ns("y").ns());
    }

    @Test
    public void testFactSpecType() {
        assertEquals("y", FactSpec.ns("x").type("y").type());
    }

    @Test
    public void testFactSpecAggId() {
        UUID id = UUID.randomUUID();
        assertEquals(id, FactSpec.ns("x").aggId(id).aggId());
    }

    @Test
    public void testFactSpecJsFilter() {
        assertEquals("foo", FactSpec.ns("x").jsFilterScript("foo").jsFilterScript());
    }

    @Test
    public void testFactSpecEquality() {
        FactSpec f1 = FactSpec.ns("x");
        FactSpec f2 = FactSpec.ns("x");
        assertNotEquals(f1, f2); // do not compare FactSpecs!
        assertNotSame(f1, f2);
    }
}
