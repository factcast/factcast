/*
 * Copyright © 2018 Mercateo AG (http://www.mercateo.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
