/*
 * Copyright © 2017-2020 factcast.org
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

import static org.assertj.core.api.Assertions.*;
import static org.factcast.core.TestHelper.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.val;

public class FactCastJsonTest {
    @Test
    void testCopyNull() {
        Assertions.assertThrows(NullPointerException.class, () -> FactCastJson.copy(null));
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
        expectNPE(() -> FactCastJson.readValue(null, (String) null));
        expectNPE(() -> FactCastJson.readValue(FactCastJson.class, (String) null));
        expectNPE(() -> FactCastJson.readValue(null, (InputStream) null));
        expectNPE(() -> FactCastJson.readValue(FactCastJson.class, (InputStream) null));
    }

    public static class X {
        String foo;

        int bar;
    }

    @Test
    void testReadValueFromInputStream() {

        X x = FactCastJson.readValue(X.class, new ByteArrayInputStream("{\"foo\":\"baz\",\"bar\":7}"
                .getBytes()));
        assertThat(x.foo).isEqualTo("baz");
        assertThat(x.bar).isEqualTo(7);
    }

    @Test
    void testWriteValueNull() {
        expectNPE(() -> FactCastJson.writeValueAsString(null));
    }

    @Test
    void testNewObjectNode() {
        ObjectNode actual = FactCastJson.newObjectNode();
        assertNotNull(actual);
        assertTrue(actual instanceof ObjectNode);
    }

    @Test
    void testToObjectNodeNonJson() {
        Assertions.assertThrows(RuntimeException.class, () -> FactCastJson.toObjectNode("no-json"));
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

    @Test
    void testWriteValueAsPrettyFromObject() {
        @Data
        class TestObject {
            String foo = "bar";
        }

        String pretty = FactCastJson.writeValueAsPrettyString(new TestObject());
        assertTrue(pretty.contains("\"foo\" : \"bar\""));
    }

    @Test
    void testReadJsonFileAsText(@TempDir Path tempDir) throws IOException {
        Path testFilePath = tempDir.resolve("test.json");

        String content = "{\"foo\":\"bar\"}";

        Files.write(testFilePath, content.getBytes());

        String val = FactCastJson.readJSON(testFilePath.toFile());
        assertTrue(val.contentEquals(content));
    }

    @Test
    void testAddSerToHeader() {
        val newHeader = FactCastJson.addSerToHeader(33, "{}");
        assertEquals("{\"meta\":{\"_ser\":33}}", newHeader);
        val updatedHeader = FactCastJson.addSerToHeader(77, newHeader);
        assertEquals("{\"meta\":{\"_ser\":77}}", updatedHeader);

        val updatedHeaderWithoutSerAttribute = FactCastJson.addSerToHeader(78,
                "{\"meta\":{\"foo\":\"bar\"}}");
        assertEquals("{\"meta\":{\"foo\":\"bar\",\"_ser\":78}}", updatedHeaderWithoutSerAttribute);
    }

    @Test
    void testToPrettyString() {
        val someJson = "{\"meta\":{\"foo\":\"bar\",\"_ser\":78}}";
        val pretty = FactCastJson.toPrettyString(someJson);
        assertEquals("{\n" +
                "  \"meta\" : {\n" +
                "    \"foo\" : \"bar\",\n" +
                "    \"_ser\" : 78\n" +
                "  }\n" +
                "}", pretty);
    }

}
