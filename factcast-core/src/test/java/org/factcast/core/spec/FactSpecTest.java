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
package org.factcast.core.spec;

import static org.assertj.core.api.AssertionsForInterfaceTypes.*;
import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.factcast.core.util.FactCastJson;
import org.factcast.factus.event.Specification;
import org.junit.jupiter.api.*;

public class FactSpecTest {

  @Test
  void testMetaBothNull() {
    Assertions.assertThrows(NullPointerException.class, () -> FactSpec.ns("foo").meta(null, null));
  }

  @Test
  void testMetaKeyNull() {
    Assertions.assertThrows(NullPointerException.class, () -> FactSpec.ns("foo").meta(null, ""));
  }

  @Test
  void testMetaValueNull() {
    Assertions.assertThrows(NullPointerException.class, () -> FactSpec.ns("foo").meta("", null));
  }

  @Test
  void testFactSpecConstructorNull() {
    Assertions.assertThrows(NullPointerException.class, () -> new FactSpec(null));
  }

  @SuppressWarnings("static-access")
  @Test
  void testFactSpecNs() {
    assertEquals("y", FactSpec.ns("x").ns("y").ns());
  }

  @Test
  void testFactSpecType() {
    assertEquals("y", FactSpec.ns("x").type("y").type());
  }

  @Test
  void testFactSpecVersion() {
    assertEquals(1, FactSpec.ns("x").type("y").version(1).version());
  }

  @Test
  void testFactSpecAggId() {
    UUID id = UUID.randomUUID();
    assertEquals(id, FactSpec.ns("x").aggId(id).aggId());
  }

  @Test
  void testFactSpecJsFilter() {
    FactSpec ns = FactSpec.ns("x");
    ns = ns.jsFilterScript("foo");
    String script = ns.jsFilterScript();
    assertEquals("foo", script);
  }

  @Test
  void testFactSpecEquality() {
    FactSpec f1 = FactSpec.ns("x");
    FactSpec f2 = FactSpec.ns("x");
    assertEquals(f1, f2);
    assertNotSame(f1, f2);
  }

  @Test
  public void testJsFilterScriptDeserDownwardCompatibility() {
    String script = "foo";
    String json = "{\"ns\":\"x\",\"jsFilterScript\":\"" + script + "\"}";

    FactSpec spec = FactCastJson.readValue(FactSpec.class, json);

    assertEquals(new FilterScript("js", script), spec.filterScript());
  }

  @Test
  public void testJsFilterScriptDeserRemoved() {
    String script = "foo";
    String json = "{\"ns\":\"x\",\"jsFilterScript\":\"" + script + "\"}";

    FactSpec spec = FactCastJson.readValue(FactSpec.class, json);
    spec.filterScript(null);
    assertNull(spec.jsFilterScript());
    assertNull(spec.filterScript());
  }

  @Test
  public void testFilterScriptDeser() {
    String script = "foo";
    String json =
        "{\"ns\":\"x\",\"filterScript\":{\"languageIdentifier\":\"js\",\"source\":\""
            + script
            + "\"}}";

    FactSpec spec = FactCastJson.readValue(FactSpec.class, json);
    assertEquals(script, spec.jsFilterScript());
    assertEquals(FilterScript.js(script), spec.filterScript());

    spec.filterScript(null);
    assertNull(spec.jsFilterScript());
    assertNull(spec.filterScript());
  }

  @Test
  public void testJsFilterScriptSerDownwardCompatibility() {
    String expected = "foo";
    FactSpec fs = FactSpec.ns("x").filterScript(FilterScript.js("foo"));
    ObjectNode node = FactCastJson.toObjectNode(FactCastJson.writeValueAsString(fs));

    assertEquals(expected, node.get("jsFilterScript").asText());
  }

  @Specification(ns = "ns")
  static class TestFactPayload {}

  @Test
  public void testFactSpecFromAnnotation1() {
    FactSpec factSpec = FactSpec.from(TestFactPayload.class);

    assertEquals("ns", factSpec.ns());
    assertEquals("TestFactPayload", factSpec.type());
    assertEquals(0, factSpec.version());
  }

  @Specification(ns = "ns", type = "type")
  public static class TestFactWithType {}

  @Test
  public void testFactSpecFromAnnotation2() {
    FactSpec factSpec = FactSpec.from(TestFactWithType.class);

    assertEquals("ns", factSpec.ns());
    assertEquals("type", factSpec.type());
    assertEquals(0, factSpec.version());
  }

  @Specification(ns = "ns", type = "type", version = 2)
  public static class TestFactWithTypeAndVersion {}

  @Test
  public void testFactSpecFromAnnotation3() {
    FactSpec factSpec = FactSpec.from(TestFactWithTypeAndVersion.class);

    assertEquals("ns", factSpec.ns());
    assertEquals("type", factSpec.type());
    assertEquals(2, factSpec.version());
  }

  @Test
  public void testThrowIfNoAnnotationSpecPresent() {
    Assertions.assertThrows(
        IllegalArgumentException.class, () -> FactSpec.from(Specification.class));
  }

  @Test
  public void testFromVarArgs() {
    List<FactSpec> spec = FactSpec.from(TestFactWithType.class, TestFactWithTypeAndVersion.class);
    assertThat(spec)
        .hasSize(2)
        .contains(FactSpec.ns("ns").type("type").version(2))
        .contains(FactSpec.ns("ns").type("type"));
  }

  @Test
  public void testFromList() {
    List<FactSpec> spec =
        FactSpec.from(Arrays.asList(TestFactWithType.class, TestFactWithTypeAndVersion.class));
    assertThat(spec)
        .hasSize(2)
        .contains(FactSpec.ns("ns").type("type").version(2))
        .contains(FactSpec.ns("ns").type("type"));
  }

  @Test
  void testCopy() {
    FactSpec org = FactSpec.from(TestFactWithType.class);
    FactSpec copy = org.copy();
    assertThat(copy).isEqualTo(org);
    assertThat(copy).isNotSameAs(org);

    org.meta("foo", "bar");
    assertThat(copy).isNotEqualTo(org);
  }
}
