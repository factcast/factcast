package org.factcast.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.factcast.core.util.FactCastJson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.fasterxml.jackson.databind.JsonNode;

public class Fact0Test {

    @Test
    public void testOfNull1() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            Fact.of(null, "");
        });
    }

    @Test
    public void testOfNull2() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            Fact.of("", null);
        });
    }

    @Test
    public void testOfNull() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            Fact.of((String) null, null);
        });
    }

    @Test
    public void testOf() {
        Test0Fact f = new Test0Fact();
        Fact f2 = Fact.of(f.jsonHeader(), f.jsonPayload());
        assertEquals(f.id(), f2.id());
    }

    @Test
    public void testBefore() {
        Fact one = Fact.of("{" + "\"ns\":\"ns\"," + "\"id\":\"" + UUID.randomUUID() + "\","
                + "\"meta\":{ \"_ser\":1 }" + "}", "{}");
        Fact two = Fact.of("{" + "\"ns\":\"ns\"," + "\"id\":\"" + UUID.randomUUID() + "\","
                + "\"meta\":{ \"_ser\":2 }" + "}", "{}");
        Fact three = Fact.of("{" + "\"ns\":\"ns\"," + "\"id\":\"" + UUID.randomUUID() + "\","
                + "\"meta\":{ \"_ser\":3 }" + "}", "{}");
        assertTrue(one.before(two));
        assertTrue(two.before(three));
        assertTrue(one.before(three));
        assertFalse(one.before(one));
        assertFalse(two.before(one));
        assertFalse(three.before(one));
        assertFalse(three.before(two));
    }

    @Test
    public void testSerialUnset() {
        Assertions.assertThrows(IllegalStateException.class, () -> {
            Fact.of("{" + "\"ns\":\"ns\"," + "\"id\":\"" + UUID.randomUUID() + "\"" + "}", "{}")
                    .serial();
        });
    }

    public void testBuilderDefaults() {
        Fact f = Fact.builder().build("{\"a\":1}");
        assertEquals("default", f.ns());
        assertNotNull(f.id());
        assertEquals("{\"a\":1}", f.jsonPayload());
    }

    @Test
    public void testBuilder() {
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

    @Test
    public void testOfJsonNodeJsonNodeNull1() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            Fact.of(null, Mockito.mock(JsonNode.class));
        });
    }

    @Test
    public void testOfJsonNodeJsonNodeNull2() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            Fact.of(Mockito.mock(JsonNode.class), null);
        });
    }

    @Test
    public void testOfJsonNodeJsonNodeNull() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            Fact.of((JsonNode) null, null);
        });
    }

    @Test
    public void testOfJsonNode() {
        JsonNode payload = FactCastJson.newObjectNode();
        String headerString = "{\"id\":\"" + UUID.randomUUID() + "\",\"ns\":\"ns\"}";
        JsonNode header = FactCastJson.toObjectNode(headerString);
        assertEquals(headerString, Fact.of(header, payload).jsonHeader());
    }
}
