package org.factcast.core;

import static org.junit.Assert.*;

import java.util.UUID;

import org.junit.Test;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;

public class DefaultFactTest {

    @Test(expected = NullPointerException.class)
    public void testNullHeader() throws Exception {
        DefaultFact.of("{}", null);
    }

    @Test(expected = NullPointerException.class)
    public void testNullPayload() throws Exception {
        DefaultFact.of(null, "{}");
    }

    @Test(expected = NullPointerException.class)
    public void testNullContracts() throws Exception {
        DefaultFact.of(null, null);
    }

    @Test(expected = JsonParseException.class)
    public void testUnparsableHeader() throws Exception {
        DefaultFact.of("not json at all", "{}");
    }

    @Test(expected = JsonMappingException.class)
    public void testNoId() throws Exception {
        DefaultFact.of("{\"ns\":\"default\"}", "{}");
    }

    @Test(expected = JsonMappingException.class)
    public void testIdNotUUID() throws Exception {
        DefaultFact.of("{\"id\":\"buh\",\"ns\":\"default\"}", "{}");
    }

    @Test
    public void testValidFact() throws Exception {
        DefaultFact.of("{\"id\":\"" + UUID.randomUUID() + "\",\"ns\":\"default\"}", "{}");
    }

    @Test
    public void testMetaDeser() throws Exception {
        Fact f = DefaultFact.of("{\"id\":\"" + UUID.randomUUID()
                + "\",\"ns\":\"default\",\"meta\":{\"foo\":7}}", "{}");
        assertEquals("7", f.meta("foo"));
    }
}
