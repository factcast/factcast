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
package org.factcast.core;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.core.JsonParseException;
import java.io.*;
import java.util.UUID;
import org.junit.jupiter.api.*;

public class DefaultFactTest {

  @Test
  void testNullHeader() {
    Assertions.assertThrows(NullPointerException.class, () -> DefaultFact.of("{}", null));
  }

  @Test
  void testNullPayload() {
    Assertions.assertThrows(NullPointerException.class, () -> DefaultFact.of(null, "{}"));
  }

  @Test
  void testUnparseableHeader() {
    Assertions.assertThrows(
        JsonParseException.class, () -> DefaultFact.of("not json at all", "{}"));
  }

  @Test
  void testNoId() {
    Assertions.assertThrows(
        IllegalArgumentException.class, () -> DefaultFact.of("{\"ns\":\"default\"}", "{}"));
  }

  @Test
  void testIdNotUUID() {
    Assertions.assertThrows(
        IOException.class, () -> DefaultFact.of("{\"id\":\"buh\",\"ns\":\"default\"}", "{}"));
  }

  @Test
  void testValidFact() {
    DefaultFact.of("{\"id\":\"" + UUID.randomUUID() + "\",\"ns\":\"default\"}", "{}");
  }

  @Test
  void testMetaDeser() {
    Fact f =
        DefaultFact.of(
            "{\"id\":\"" + UUID.randomUUID() + "\",\"ns\":\"default\",\"meta\":{\"foo\":7}}", "{}");
    assertEquals("7", f.meta("foo"));
  }

  @Test
  void testExternalization() throws Exception {
    Fact f =
        DefaultFact.of(
            "{\"id\":\"" + UUID.randomUUID() + "\",\"ns\":\"default\",\"meta\":{\"foo\":7}}", "{}");
    Fact copy = copyBySerialization(f);
    assertEquals(f, copy);
  }

  private <T> T copyBySerialization(T f) throws IOException, ClassNotFoundException {
    ByteArrayOutputStream os = new ByteArrayOutputStream();
    ObjectOutputStream objectOutputStream = new ObjectOutputStream(os);
    objectOutputStream.writeObject(f);
    objectOutputStream.flush();
    ByteArrayInputStream is = new ByteArrayInputStream(os.toByteArray());
    ObjectInputStream objectInputStream = new ObjectInputStream(is);
    return (T) objectInputStream.readObject();
  }

  @Test
  void testJsonHeader() {
    UUID id = UUID.randomUUID();
    UUID aid = UUID.randomUUID();
    String header =
        "{\"id\":\""
            + id
            + "\",\"ns\":\"narf\",\"type\":\"foo\",\"aggIds\":[\""
            + aid
            + "\"],\"meta\":{\"foo\":7}}";
    Fact f = DefaultFact.of(header, "{}");
    assertSame(header, f.jsonHeader());
  }

  @Test
  void testEqualityBasedOnId() {
    UUID id = UUID.randomUUID();
    UUID aid = UUID.randomUUID();
    String header =
        "{\"id\":\""
            + id
            + "\",\"ns\":\"narf\",\"type\":\"foo\",\"aggIds\":[\""
            + aid
            + "\"],\"meta\":{\"foo\":7}}";
    Fact f = DefaultFact.of(header, "{}");
    Fact f2 = DefaultFact.of("{\"ns\":\"ns\",\"id\":\"" + id + "\"}", "{}");
    Fact f3 = DefaultFact.of("{\"ns\":\"ns\",\"id\":\"" + aid + "\"}", "{}");
    assertEquals(f, f);
    assertEquals(f, f2);
    assertNotEquals(f, f3);
  }

  @Test
  void testCopyAttributes() throws Exception {
    UUID id = UUID.randomUUID();
    UUID aid = UUID.randomUUID();
    Fact f =
        DefaultFact.of(
            "{\"id\":\""
                + id
                + "\",\"ns\":\"narf\",\"type\":\"foo\",\"aggIds\":[\""
                + aid
                + "\"],\"meta\":{\"foo\":7}}",
            "{}");
    Fact copy = copyBySerialization(f);
    assertNotSame(f.id(), copy.id());
    assertNotSame(f.ns(), copy.ns());
    assertNotSame(f.type(), copy.type());
    assertNotSame(f.aggIds(), copy.aggIds());
    assertNotSame(f.meta("foo"), copy.meta("foo"));
    assertEquals(f.id(), copy.id());
    assertEquals(f.ns(), copy.ns());
    assertEquals(f.type(), copy.type());
    assertEquals(f.aggIds(), copy.aggIds());
    assertEquals(f.meta("foo"), copy.meta("foo"));
    assertEquals(f.jsonPayload(), copy.jsonPayload());
  }

  @Test
  void testEqualityMustBeBasedOnIDOnly() {
    UUID id = UUID.randomUUID();
    Fact f1 =
        DefaultFact.of(
            "{\"id\":\""
                + id
                + "\",\"ns\":\"narf\",\"type\":\"foo\",\"aggIds\":[\""
                + UUID.randomUUID()
                + "\"],\"meta\":{\"foo\":7}}",
            "{}");
    Fact f2 =
        DefaultFact.of(
            "{\"id\":\""
                + id
                + "\",\"ns\":\"poit\",\"type\":\"bar\",\"aggIds\":[\""
                + UUID.randomUUID()
                + "\"],\"meta\":{\"foo\":7}}",
            "{}");
    assertEquals(f1, f2);
  }

  @Test
  void testOfNoId() {
    Assertions.assertThrows(
        IllegalArgumentException.class, () -> Fact.of("{\"ns\":\"narf\"}", "{}"));
  }

  @Test
  void testOfNoNs() {
    Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> Fact.of("{\"id\":\"" + UUID.randomUUID() + "\"}", "{}"));
  }

  @Test
  void testOfEmptyNs() {
    Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> Fact.of("{\"id\":\"" + UUID.randomUUID() + "\",\"ns\":\"\"}", "{}"));
  }

  @Test
  void testOfInvalidVersion() {
    Assertions.assertThrows(
        IllegalArgumentException.class,
        () ->
            Fact.of(
                "{\"id\":\"" + UUID.randomUUID() + "\",\"version\":\"" + -1 + "\",\"ns\":\"ns\"}",
                "{}"));
  }

  @Test
  void testVersionPrevails() {
    assertEquals(
        7,
        Fact.of(
                "{\"id\":\"" + UUID.randomUUID() + "\",\"version\":\"" + 7 + "\",\"ns\":\"ns\"}",
                "{}")
            .version());
  }

  @Test
  void testToString() {
    UUID id = UUID.randomUUID();
    Fact fact =
        Fact.of(
            "{\"id\":\"" + id + "\",\"version\":\"" + 7 + "\",\"ns\":\"ns\"}",
            "{\"foo\":\"payload\"}");
    assertEquals("DefaultFact [id=" + id + "]", fact.toString());
  }

  @Test
  void testHeader() {
    FactHeader fh = new FactHeader();
    fh.id(UUID.randomUUID()).ns("foo");
    DefaultFact f = new DefaultFact(fh, "{}");
    assertSame(fh, f.header());
  }
}
