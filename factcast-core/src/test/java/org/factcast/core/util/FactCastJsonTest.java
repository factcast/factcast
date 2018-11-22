package org.factcast.core.util;

import static org.factcast.core.TestHelper.expectNPE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

public class FactCastJsonTest {

    @Test
    void testCopyNull() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            FactCastJson.copy(null);
        });
    }

    @Test
    void testCopy() {
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
    void testReadValueNull() {
        expectNPE(() -> FactCastJson.readValue(null, ""));
        expectNPE(() -> FactCastJson.readValue(null, null));
        expectNPE(() -> FactCastJson.readValue(FactCastJson.class, null));
    }

    @Test
    void testWriteValueNull() {
        expectNPE(() -> FactCastJson.writeValueAsString(null));
    }

    @Test
    void testNewObjectNode() {
        assertNotNull(FactCastJson.newObjectNode());
        assertTrue(FactCastJson.newObjectNode() instanceof ObjectNode);
    }

    @Test
    void testToObjectNodeNonJson() {
        Assertions.assertThrows(RuntimeException.class, () -> {
            FactCastJson.toObjectNode("no-json");
        });
    }

    @Test
    void testToObjectNode() {
        ObjectNode objectNode = FactCastJson.toObjectNode("{\"x\":1}");
        JsonNode jsonNode = objectNode.get("x");
        assertEquals(1, jsonNode.asInt());
        assertNull(objectNode.get("y"));
    }

    @Test
    void testWriteValueAsPrettyString() {
        String json = "{\"a\":1}";
        String pretty = FactCastJson.writeValueAsPrettyString(FactCastJson.toObjectNode(json));
        assertTrue(pretty.contains("\n"));
        assertTrue(pretty.contains(" "));
    }
}
