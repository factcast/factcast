package org.factcast.core.util;

import static org.factcast.core.TestHelper.*;
import static org.junit.Assert.*;

import org.junit.Test;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

public class FactCastJson0Test {

    @Test(expected = NullPointerException.class)
    public void testCopyNull() throws Exception {
        FactCastJson.copy(null);
    }

    @Test
    public void testCopy() throws Exception {
        final Foo foo = new Foo("bar", "baz");
        Foo copy = FactCastJson.copy(foo);

        assertNotSame(foo, copy);
        assertNotEquals(foo, copy);

        assertEquals(foo.bar(), copy.bar());
        assertNull(copy.baz());

    }

    @AllArgsConstructor
    @Data
    @NoArgsConstructor
    static class Foo {
        @JsonProperty
        String bar;

        @JsonIgnore
        String baz;
    }

    @Test
    public void testReadValueNull() throws Exception {
        expectNPE(() -> FactCastJson.readValue(null, ""));
        expectNPE(() -> FactCastJson.readValue(null, null));
        expectNPE(() -> FactCastJson.readValue(FactCastJson.class, null));
    }

    @Test
    public void testWriteValueNull() throws Exception {
        expectNPE(() -> FactCastJson.writeValueAsString(null));
    }

    @Test
    public void testNewObjectNode() throws Exception {
        assertNotNull(FactCastJson.newObjectNode());
        assertTrue(FactCastJson.newObjectNode() instanceof ObjectNode);
    }

    @Test(expected = RuntimeException.class)
    public void testToObjectNodeNonJson() throws Exception {
        FactCastJson.toObjectNode("no-json");
    }

    @Test()
    public void testToObjectNode() throws Exception {
        ObjectNode objectNode = FactCastJson.toObjectNode("{\"x\":1}");
        JsonNode jsonNode = objectNode.get("x");
        assertEquals(1, jsonNode.asInt());
        assertNull(objectNode.get("y"));
    }

}
