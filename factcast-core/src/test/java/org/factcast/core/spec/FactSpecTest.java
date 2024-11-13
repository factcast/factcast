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

import static org.assertj.core.api.AssertionsForClassTypes.*;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.util.*;
import lombok.NonNull;
import lombok.SneakyThrows;
import org.factcast.core.util.FactCastJson;
import org.factcast.factus.event.Specification;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class FactSpecTest {

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
    assertThatThrownBy(() -> FactSpec.ns(" ")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new FactSpec(" ")).isInstanceOf(IllegalArgumentException.class);
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
    assertThat(FactSpec.ns("x").aggId(id).aggIds()).containsOnly(id);
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

  @SneakyThrows
  @Test
  void testFactSpecMultipleAggIdsCompatibility() {
    Set<UUID> ids = new HashSet<>();
    UUID id1 = UUID.randomUUID();
    UUID id2 = UUID.randomUUID();
    UUID id3 = UUID.randomUUID();
    ids.add(id1);
    ids.add(id2);
    ids.add(id3);

    FactSpec fs = FactSpec.ns("x").aggId(id2, id3);
    Field aggIdField = FactSpec.class.getDeclaredField("aggId");
    aggIdField.setAccessible(true);
    aggIdField.set(fs, id1);
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

  @SneakyThrows
  @Test
  void testFactSpecEqualityCompatibility() {
    UUID id2 = new UUID(0, 2);
    UUID id1 = new UUID(0, 1);

    FactSpec f1 = FactSpec.ns("x").aggId(id2);

    Field aggIdField = FactSpec.class.getDeclaredField("aggId");
    aggIdField.setAccessible(true);
    aggIdField.set(f1, id1);

    FactSpec f2 = FactSpec.ns("x").aggId(id1, id2);
    assertEquals(f1, f2);
    assertNotSame(f1, f2);
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
    Assertions.assertThrows(
        IllegalArgumentException.class, () -> FactSpec.from(Specification.class));
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
}
