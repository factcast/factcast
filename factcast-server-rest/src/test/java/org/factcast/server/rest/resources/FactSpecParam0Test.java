package org.factcast.server.rest.resources;

import static org.junit.Assert.*;

import java.util.UUID;

import org.junit.Test;

public class FactSpecParam0Test {

    private FactSpecParam uut = new FactSpecParam();

    @Test(expected = NullPointerException.class)
    public void testNsMustNotBeNull() throws Exception {
        uut.ns(null);
    }

    @Test
    public void testNs() throws Exception {
        assertEquals("foo", uut.ns("foo").ns());
    }

    @Test
    public void testType() throws Exception {
        assertEquals("foo", uut.type("foo").type());
    }

    @Test
    public void testAggId() throws Exception {
        UUID id = UUID.randomUUID();
        assertEquals(id, uut.aggId(id).aggId());
    }

    @Test
    public void testMeta() throws Exception {
        assertNull(uut.meta().get("foo"));
        uut.meta().put("foo", "bar");
        assertEquals("bar", uut.meta().get("foo"));
    }
}
