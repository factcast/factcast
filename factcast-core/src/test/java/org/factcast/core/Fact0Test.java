package org.factcast.core;

import static org.junit.Assert.*;

import java.util.UUID;

import org.factcast.core.util.FactCastJson;
import org.junit.Test;
import org.mockito.Mockito;

import com.fasterxml.jackson.databind.JsonNode;

public class Fact0Test {

    @Test(expected = NullPointerException.class)
    public void testOfNull1() throws Exception {
        Fact.of(null, "");
    }

    @Test(expected = NullPointerException.class)
    public void testOfNull2() throws Exception {
        Fact.of("", null);
    }

    @Test(expected = NullPointerException.class)
    public void testOfNull() throws Exception {
        Fact.of((String) null, null);
    }

    @Test
    public void testOf() throws Exception {
        Test0Fact f = new Test0Fact();
        Fact f2 = Fact.of(f.jsonHeader(), f.jsonPayload());

        assertEquals(f.id(), f2.id());
    }

    @Test
    public void testBefore() throws Exception {
        Fact one = Fact.of("{" +
                "\"ns\":\"ns\"," +
                "\"id\":\"" + UUID.randomUUID() + "\"," +
                "\"meta\":{ \"_ser\":1 }" +
                "}", "{}");
        Fact two = Fact.of("{" +
                "\"ns\":\"ns\"," +
                "\"id\":\"" + UUID.randomUUID() + "\"," +
                "\"meta\":{ \"_ser\":2 }" +
                "}", "{}");
        Fact three = Fact.of("{" +
                "\"ns\":\"ns\"," +
                "\"id\":\"" + UUID.randomUUID() + "\"," +
                "\"meta\":{ \"_ser\":3 }" +
                "}", "{}");

        assertTrue(one.before(two));
        assertTrue(two.before(three));
        assertTrue(one.before(three));

        assertFalse(one.before(one));
        assertFalse(two.before(one));
        assertFalse(three.before(one));
        assertFalse(three.before(two));

    }

    @Test(expected = IllegalStateException.class)
    public void testSerialUnset() throws Exception {
        Fact.of("{" +
                "\"ns\":\"ns\"," +
                "\"id\":\"" + UUID.randomUUID() + "\"" +
                "}", "{}").serial();

    }

    public void testBuilderDefaults() throws Exception {
        Fact f = Fact.builder().build("{\"a\":1}");
        assertEquals("default", f.ns());
        assertNotNull(f.id());
        assertEquals("{\"a\":1}", f.jsonPayload());
    }

    @Test
    public void testBuilder() throws Exception {
        UUID aggId1 = UUID.randomUUID();
        UUID aggId2 = UUID.randomUUID();
        UUID aggId3 = UUID.randomUUID();
        UUID factId = UUID.randomUUID();

        Fact f = Fact.builder()
                .ns("ns")
                .type("type")
                .aggId(aggId1)
                .aggId(aggId2)
                .aggId(aggId3)
                .meta("foo", "bar")
                .meta("buh", "bang")
                .id(factId)
                .build("{\"a\":2}");

        assertEquals("ns", f.ns());
        assertEquals("type", f.type());
        assertTrue(f.aggIds().contains(aggId1));
        assertTrue(f.aggIds().contains(aggId2));
        assertTrue(f.aggIds().contains(aggId3));
        assertFalse(f.aggIds().contains(factId));
        assertEquals("bar", f.meta("foo"));
        assertEquals("bang", f.meta("buh"));
        assertEquals("{\"a\":2}", f.jsonPayload());
    }

    @Test(expected = NullPointerException.class)
    public void testOfJsonNodeJsonNodeNull1() throws Exception {
        Fact.of(null, Mockito.mock(JsonNode.class));
    }

    @Test(expected = NullPointerException.class)
    public void testOfJsonNodeJsonNodeNull2() throws Exception {
        Fact.of(Mockito.mock(JsonNode.class), null);
    }

    @Test(expected = NullPointerException.class)
    public void testOfJsonNodeJsonNodeNull() throws Exception {
        Fact.of((JsonNode) null, null);
    }

    @Test
    public void testOfJsonNode() throws Exception {
        JsonNode payload = FactCastJson.newObjectNode();
        String headerString = "{\"id\":\"" + UUID.randomUUID() + "\",\"ns\":\"ns\"}";
        JsonNode header = FactCastJson.toObjectNode(headerString);
        assertEquals(headerString, Fact.of(header, payload).jsonHeader());

    }
}
