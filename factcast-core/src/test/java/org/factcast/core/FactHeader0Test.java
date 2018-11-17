package org.factcast.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

public class FactHeader0Test {

    @Test
    public void testDeserializability() throws Exception {
        DefaultFact.Header h = new ObjectMapper().readValue(
                "{\"id\":\"5d0e3ae9-6684-42bc-87a7-854f76506f7e\",\"ns\":\"ns\",\"type\":\"t\",\"meta\":{\"foo\":\"bar\"}}",
                DefaultFact.Header.class);
        assertEquals(UUID.fromString("5d0e3ae9-6684-42bc-87a7-854f76506f7e"), h.id());
        assertEquals("ns", h.ns());
        assertEquals("t", h.type());
        assertEquals("bar", h.meta().get("foo"));
    }

    @Test
    public void testIgnoreExtra() throws Exception {
        DefaultFact.Header h = new ObjectMapper().readValue(
                "{\"id\":\"5d0e3ae9-6684-42bc-87a7-854f76506f7e\",\"ns\":\"ns\",\"type\":\"t\",\"bing\":\"bang\"}",
                DefaultFact.Header.class);
        assertEquals(UUID.fromString("5d0e3ae9-6684-42bc-87a7-854f76506f7e"), h.id());
        assertEquals("ns", h.ns());
        assertEquals("t", h.type());
    }
}
