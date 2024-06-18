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
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.*;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

class FactCastJsonTest {
  @Test
  void testCopyNull() {
    Assertions.assertThrows(NullPointerException.class, () -> FactCastJson.copy(null));
  }

  @Test
  void testCopy() {
    Foo foo = new Foo("bar", "baz");
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

    @JsonProperty String bar;

    @JsonIgnore String baz;
  }

  @Test
  void testReadValueNull() {
    expectNPE(() -> FactCastJson.readValue((Class) null, ""));
    expectNPE(() -> FactCastJson.readValue((Class) null, (String) null));
    expectNPE(() -> FactCastJson.readValue(FactCastJson.class, (String) null));
    expectNPE(() -> FactCastJson.readValue(null, (InputStream) null));
    expectNPE(() -> FactCastJson.readValue(FactCastJson.class, (InputStream) null));
  }

  static class TestClassWithValue {
    int value;
  }

  @Test
  void testReadValueWithClass() {
    assertEquals(7, FactCastJson.readValue(TestClassWithValue.class, "{\"value\":7}").value);
  }

  @Test
  void testReadValueWithTypeRef() {
    assertEquals(
        7,
        FactCastJson.readValue(new TypeReference<TestClassWithValue>() {}, "{\"value\":7}").value);
  }

  public static class X {
    String foo;

    int bar;
  }

  @Test
  void testReadValueFromInputStream() {

    X x =
        FactCastJson.readValue(
            X.class, new ByteArrayInputStream("{\"foo\":\"baz\",\"bar\":7}".getBytes()));
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

    String value = FactCastJson.readJSON(testFilePath.toFile());
    assertTrue(value.contentEquals(content));
  }

  @Test
  void testAddSerToHeader() {
    String newHeader = FactCastJson.addSerToHeader(33, "{}");
    assertEquals("{\"meta\":{\"_ser\":33}}", newHeader);
    String updatedHeader = FactCastJson.addSerToHeader(77, newHeader);
    assertEquals("{\"meta\":{\"_ser\":77}}", updatedHeader);

    String updatedHeaderWithoutSerAttribute =
        FactCastJson.addSerToHeader(78, "{\"meta\":{\"foo\":\"bar\"}}");
    assertEquals("{\"meta\":{\"foo\":\"bar\",\"_ser\":78}}", updatedHeaderWithoutSerAttribute);
  }

  @Test
  void testToPrettyString() {
    String someJson = "{\"meta\":{\"foo\":\"bar\",\"_ser\":78}}";
    String pretty = FactCastJson.toPrettyString(someJson);
    assertEquals(
        "{\n"
            + "  \"meta\" : {\n"
            + "    \"foo\" : \"bar\",\n"
            + "    \"_ser\" : 78\n"
            + "  }\n"
            + "}",
        pretty);
  }

  @Test
  void testValueToTree() throws Exception {
    ObjectMapper om = Mockito.mock(ObjectMapper.class);
    try (AutoCloseable reset = FactCastJson.replaceObjectMapper(om)) {

      UUID probe = UUID.randomUUID();
      FactCastJson.valueToTree(probe);

      Mockito.verify(om).valueToTree(ArgumentMatchers.same(probe));
    }
  }

  @Test
  void testReadTree() throws Exception {
    ObjectMapper om = Mockito.mock(ObjectMapper.class);
    try (AutoCloseable reset = FactCastJson.replaceObjectMapper(om)) {

      String probe = UUID.randomUUID().toString();
      FactCastJson.readTree(probe);

      Mockito.verify(om).readTree(probe);
    }
  }

  @Test
  void convertValue() throws Exception {
    ObjectMapper om = Mockito.mock(ObjectMapper.class);
    try (AutoCloseable reset = FactCastJson.replaceObjectMapper(om)) {

      String probe = UUID.randomUUID().toString();
      FactCastJson.convertValue(probe, Integer.class);

      Mockito.verify(om).convertValue(probe, Integer.class);
    }
  }

  @Test
  void toJsonNode() throws Exception {
    ObjectMapper om = Mockito.mock(ObjectMapper.class);
    try (AutoCloseable reset = FactCastJson.replaceObjectMapper(om)) {

      Map<String, Object> probe = new HashMap<>();
      FactCastJson.toJsonNode(probe);

      Mockito.verify(om).convertValue(probe, JsonNode.class);
    }
  }

  @Test
  void writeValueAsBytes() throws Exception {
    ObjectMapper om = Mockito.mock(ObjectMapper.class);
    try (AutoCloseable reset = FactCastJson.replaceObjectMapper(om)) {

      Map<String, Object> probe = new HashMap<>();
      FactCastJson.writeValueAsBytes(probe);

      Mockito.verify(om).writeValueAsBytes(probe);
    }
  }

  @Test
  void readValueFromBytes() throws Exception {
    ObjectMapper om = Mockito.mock(ObjectMapper.class);
    try (AutoCloseable reset = FactCastJson.replaceObjectMapper(om)) {

      ObjectReader or = mock(ObjectReader.class);
      when(om.readerFor(String.class)).thenReturn(or);

      byte[] probe = "foo".getBytes();
      FactCastJson.readValueFromBytes(String.class, probe);

      Mockito.verify(om).readerFor(String.class);
      Mockito.verify(or).readValue(probe);
    }
  }

  @Test
  void writeValueAsString() throws Exception {
    ObjectMapper om = Mockito.mock(ObjectMapper.class);
    try (AutoCloseable reset = FactCastJson.replaceObjectMapper(om)) {

      UUID probe = UUID.randomUUID();
      FactCastJson.writeValueAsString(probe);

      Mockito.verify(om).writeValueAsString(probe);
    }
  }

  @Test
  void objectMapper() throws Exception {
    ObjectMapper om = Mockito.mock(ObjectMapper.class);
    try (AutoCloseable reset = FactCastJson.replaceObjectMapper(om)) {

      assertSame(om, FactCastJson.mapper());
    }
  }

  @Test
  void newArrayNode() throws Exception {
    assertThat(FactCastJson.newArrayNode()).isNotNull().matches(an -> !an.iterator().hasNext());
  }
}
