package org.factcast.core;

import static org.junit.Assert.*;

import org.junit.Test;

public class MarkFact0Test {
    final MarkFact uut = new MarkFact();

    @Test
    public void testJsonPayload() {
        assertEquals("{}", new MarkFact().jsonPayload());
    }

    @Test
    public void testJsonHeader() {
        assertNotNull(uut.jsonHeader());
        // intentionally not using the constants here. i am sure you see why :)
        assertEquals("_", uut.ns());
        assertEquals("_mark", uut.type());
        assertTrue(uut.aggIds().isEmpty());
        assertNotNull(uut.id());
    }

    @Test
    public void testMeta() {
        assertNull(new MarkFact().meta("any"));
    }

}
