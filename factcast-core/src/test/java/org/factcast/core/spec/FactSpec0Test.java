package org.factcast.core.spec;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import java.util.UUID;

import org.factcast.core.MarkFact;
import org.junit.Test;

//TODO remove?
public class FactSpec0Test {

    @Test
    public void testMarkMatcher() throws Exception {
        assertTrue(new FactSpecMatcher(FactSpec.forMark()).test(new MarkFact()));
    }

    @Test(expected = NullPointerException.class)
    public void testMetaBothNull() throws Exception {
        FactSpec.ns("foo").meta(null, null);
    }

    @Test(expected = NullPointerException.class)
    public void testMetaKeyNull() throws Exception {
        FactSpec.ns("foo").meta(null, "");
    }

    @Test(expected = NullPointerException.class)
    public void testMetaValueNull() throws Exception {
        FactSpec.ns("foo").meta("", null);
    }

    @Test(expected = NullPointerException.class)
    public void testFactSpecConstructorNull() throws Exception {
        new FactSpec(null);
    }

    @SuppressWarnings("static-access")
    @Test
    public void testFactSpecNs() throws Exception {
        assertEquals("y", FactSpec.ns("x").ns("y").ns());
    }

    @Test
    public void testFactSpecType() throws Exception {
        assertEquals("y", FactSpec.ns("x").type("y").type());
    }

    @Test
    public void testFactSpecAggId() throws Exception {
        UUID id = UUID.randomUUID();
        assertEquals(id, FactSpec.ns("x").aggId(id).aggId());
    }

    @Test
    public void testFactSpecJsFilter() throws Exception {
        assertEquals("foo", FactSpec.ns("x").jsFilterScript("foo").jsFilterScript());
    }

    @Test
    public void testFactSpecEquality() throws Exception {
        FactSpec f1 = FactSpec.ns("x");
        FactSpec f2 = FactSpec.ns("x");
        assertFalse(f1.equals(f2)); // do not compare FactSpecs!
        assertNotSame(f1, f2);
    }
}
