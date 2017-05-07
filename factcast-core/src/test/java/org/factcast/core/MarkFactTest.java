package org.factcast.core;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

public class MarkFactTest {
    final MarkFact uut = new MarkFact();

    @Before
    public void setUp() throws Exception {
    }

    @Test
    public void testJsonPayload() throws Exception {
        assertEquals("{}", new MarkFact().jsonPayload());
    }

    @Test
    public void testJsonHeader() throws Exception {
        assertNotNull(uut.jsonHeader());
        // intentionally not using the constants here. i am sure you see why :)
        assertEquals("_", uut.ns());
        assertEquals("_mark", uut.type());
        assertTrue(uut.aggId().isEmpty());
        assertNotNull(uut.id());
    }

    @Test
    public void testMeta() throws Exception {
        assertNull(new MarkFact().meta("any"));
    }

}
