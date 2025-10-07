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

import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.AssertionsForClassTypes.*;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.*;
import java.util.*;
import lombok.NonNull;
import org.assertj.core.api.Assertions;
import org.factcast.core.util.FactCastJson;
import org.factcast.factus.event.Specification;
import org.junit.jupiter.api.Test;

class FactSpecTest {

  @Test
  void testMetaBothNull() {
    assertThrows(NullPointerException.class, () -> FactSpec.ns("foo").meta(null, null));
  }

  @Test
  void testMetaKeyNull() {
    assertThrows(NullPointerException.class, () -> FactSpec.ns("foo").meta(null, ""));
  }

  @Test
  void testMetaValueNull() {
    assertThrows(NullPointerException.class, () -> FactSpec.ns("foo").meta("", null));
  }

  @Test
  void testFactSpecConstructorNull() {
    assertThrows(NullPointerException.class, () -> new FactSpec(null));
  }

  @SuppressWarnings("static-access")
  @Test
  void testFactSpecNs() {
    assertEquals("y", FactSpec.ns("x").ns("y").ns());
  }

  @Test
  void testMetaKeyValue() {
    assertThat(FactSpec.ns("foo").meta("k", "v").meta()).containsEntry("k", "v");
  }

  @Test
  void testMetaExists() {
    assertThat(FactSpec.ns("foo").metaExists("k").metaKeyExists()).containsEntry("k", Boolean.TRUE);
  }

  @Test
  void testMetaDoesNotExist() {
    assertThat(FactSpec.ns("foo").metaDoesNotExist("k").metaKeyExists())
        .containsEntry("k", Boolean.FALSE);
  }

  @Test
  void shouldThrowWhenNSisEmpty() {
    Assertions.assertThatThrownBy(() -> FactSpec.ns(" "))
        .isInstanceOf(IllegalArgumentException.class);
    Assertions.assertThatThrownBy(() -> new FactSpec(" "))
        .isInstanceOf(IllegalArgumentException.class);
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
  void testFactSpecMultipleAggIds() {
    Set<UUID> ids = new HashSet<>();
    UUID id1 = UUID.randomUUID();
    UUID id2 = UUID.randomUUID();
    UUID id3 = UUID.randomUUID();
    ids.add(id1);
    ids.add(id2);
    ids.add(id3);
    assertEquals(ids, FactSpec.ns("x").aggId(id1, id2, id3).aggIds());
  }

  @Test
  void testFactSpecEmptyAggIds() {
    assertEquals(Collections.emptySet(), FactSpec.ns("x").aggIds());
  }

  @Test
  void testFactSpecSingleAggIds() {
    @NonNull UUID id = UUID.randomUUID();
    assertThat(FactSpec.ns("x").aggId(id).aggIds()).hasSize(1).containsOnly(id);
  }

  @Test
  void testFactSpecJsFilter() {
    FactSpec ns = FactSpec.ns("x");
    ns = ns.filterScript(FilterScript.js("foo"));
    String script = ns.filterScript().source();
    assertEquals("foo", script);
  }

  @Test
  void testFactSpecEquality() {
    FactSpec f1 = FactSpec.ns("x").aggId(new UUID(0, 2), new UUID(0, 1));
    FactSpec f2 = FactSpec.ns("x").aggId(new UUID(0, 1), new UUID(0, 2));
    assertEquals(f1, f2);
    assertNotSame(f1, f2);
  }

  @Test
  void testJsFilterScriptDeserRemoved() {
    String script = "foo";
    String json = "{\"ns\":\"x\",\"jsFilterScript\":\"" + script + "\"}";

    FactSpec spec = FactCastJson.readValue(FactSpec.class, json);
    spec.filterScript(null);
    assertNull(spec.filterScript());
  }

  @Test
  void testFilterScriptDeser() {
    String script = "foo";
    String json =
        "{\"ns\":\"x\",\"filterScript\":{\"languageIdentifier\":\"js\",\"source\":\""
            + script
            + "\"}}";

    FactSpec spec = FactCastJson.readValue(FactSpec.class, json);
    assertEquals(FilterScript.js(script), spec.filterScript());

    spec.filterScript(null);
    assertNull(spec.filterScript());
  }

  @Specification(ns = "ns")
  static class TestFactPayload {}

  @Test
  void testFactSpecFromAnnotation1() {
    FactSpec factSpec = FactSpec.from(TestFactPayload.class);

    assertEquals("ns", factSpec.ns());
    assertEquals("TestFactPayload", factSpec.type());
    assertEquals(0, factSpec.version());
  }

  @Specification(ns = "ns", type = "type")
  public static class TestFactWithType {}

  @Test
  void testFactSpecFromAnnotation2() {
    FactSpec factSpec = FactSpec.from(TestFactWithType.class);

    assertEquals("ns", factSpec.ns());
    assertEquals("type", factSpec.type());
    assertEquals(0, factSpec.version());
  }

  @Specification(ns = "ns", type = "type", version = 2)
  public static class TestFactWithTypeAndVersion {}

  @Test
  void testFactSpecFromAnnotation3() {
    FactSpec factSpec = FactSpec.from(TestFactWithTypeAndVersion.class);

    assertEquals("ns", factSpec.ns());
    assertEquals("type", factSpec.type());
    assertEquals(2, factSpec.version());
  }

  @Test
  void testThrowIfNoAnnotationSpecPresent() {
    assertThrows(IllegalArgumentException.class, () -> FactSpec.from(Specification.class));
  }

  @Test
  void testFromVarArgs() {
    List<FactSpec> spec = FactSpec.from(TestFactWithType.class, TestFactWithTypeAndVersion.class);
    assertThat(spec)
        .hasSize(2)
        .contains(FactSpec.ns("ns").type("type").version(2))
        .contains(FactSpec.ns("ns").type("type"));
  }

  @Test
  void testFromList() {
    List<FactSpec> spec =
        FactSpec.from(Arrays.asList(TestFactWithType.class, TestFactWithTypeAndVersion.class));
    assertThat(spec)
        .hasSize(2)
        .contains(FactSpec.ns("ns").type("type").version(2))
        .contains(FactSpec.ns("ns").type("type"));
  }

  @Test
  void testCopy() {
    FactSpec org =
        FactSpec.from(TestFactWithType.class).aggId(UUID.randomUUID(), UUID.randomUUID());
    FactSpec copy = org.copy();
    assertThat(copy).isEqualTo(org);
    assertThat(copy).isNotSameAs(org);

    org.meta("foo", "bar");
    assertThat(copy).isNotEqualTo(org);
  }

  @Test
  void testCopyWithMeta() {
    FactSpec org =
        FactSpec.from(TestFactWithType.class)
            .aggId(UUID.randomUUID(), UUID.randomUUID())
            .meta("foo", "bar");
    FactSpec copy = org.copy();
    assertThat(copy).isEqualTo(org);
    assertThat(copy).isNotSameAs(org);
  }

  @Test
  void testCopyWithMetaExists() {
    FactSpec org =
        FactSpec.from(TestFactWithType.class)
            .aggId(UUID.randomUUID(), UUID.randomUUID())
            .meta("foo", "bar")
            .metaDoesNotExist("void");
    FactSpec copy = org.copy();
    assertThat(copy).isEqualTo(org);
    assertThat(copy).isNotSameAs(org);
  }

  @Test
  void aggIdProperty() {
    UUID id = UUID.randomUUID();
    FactSpec factSpec = FactSpec.from(TestFactWithType.class).aggIdProperty("the.path", id);
    assertThat(factSpec.aggIds()).contains(id);
    Assertions.assertThat(factSpec.aggIdProperties().get("the.path")).isEqualTo(id);
  }

  @Test
  void withNs() {
    UUID aggId = UUID.randomUUID();
    UUID aggId2 = UUID.randomUUID();
    FactSpec factSpec =
        FactSpec.from(TestFactWithType.class)
            .aggIdProperty("the.path", aggId2)
            .version(2)
            .metaExists("mustExist")
            .meta("k", "v")
            .metaDoesNotExist("mustNotExist")
            .filterScript(FilterScript.js("not a script"))
            .aggId(aggId);

    FactSpec ns2 = factSpec.withNs("ns2");
    FactSpec ns = ns2.withNs("ns");

    Assertions.assertThat(ns2.ns()).isEqualTo("ns2");
    Assertions.assertThat(FactCastJson.writeValueAsPrettyString(factSpec))
        .isEqualTo(FactCastJson.writeValueAsPrettyString(ns));
  }
}
