package org.factcast.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class MarkFactTest {

    final MarkFact uut = new MarkFact();

    @Test
    void testJsonPayload() {
        assertEquals("{}", new MarkFact().jsonPayload());
    }

    @Test
    void testJsonHeader() {
        assertNotNull(uut.jsonHeader());
        // intentionally not using the constants here. i am sure you see why :)
        assertEquals("_", uut.ns());
        assertEquals("_mark", uut.type());
        assertTrue(uut.aggIds().isEmpty());
        assertNotNull(uut.id());
    }

    @Test
    void testMeta() {
        assertNull(new MarkFact().meta("any"));
    }
}
