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

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Sets;
import java.util.*;
import lombok.*;
import org.assertj.core.api.Assertions;
import org.factcast.core.util.FactCastJson;
import org.factcast.factus.event.*;
import org.factcast.factus.event.EventObject;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class FactTest {

  @Test
  void testOfNull1() {
    assertThrows(NullPointerException.class, () -> Fact.of(null, ""));
  }

  @Test
  void testOfNull2() {
    assertThrows(NullPointerException.class, () -> Fact.of("", null));
  }

  @Test
  void testOfNull() {
    assertThrows(NullPointerException.class, () -> Fact.of((String) null, null));
  }

  @Test
  void testVersion0() {
    assertThrows(IllegalArgumentException.class, () -> Fact.builder().version(0));
  }

  @Test
  void testVersionNegative() {
    assertThrows(IllegalArgumentException.class, () -> Fact.builder().version(-3));
  }

  @Test
  void testOf() {
    TestFact f = new TestFact();
    Fact f2 = Fact.of(f.jsonHeader(), f.jsonPayload());
    assertEquals(f.id(), f2.id());
  }

  @Test
  void testTsMissing() {
    Fact one =
        Fact.of(
            "{"
                + "\"ns\":\"ns\","
                + "\"id\":\""
                + UUID.randomUUID()
                + "\","
                + "\"meta\":{ \"_ser\":1 }"
                + "}",
            "{}");
    assertThat(one.timestamp()).isNull();
  }

  @Test
  void testTs() {
    long ts = 1234567;
    Fact one =
        Fact.of(
            "{"
                + "\"ns\":\"ns\","
                + "\"id\":\""
                + UUID.randomUUID()
                + "\","
                + "\"meta\":{ \"_ser\":1 , \"_ts\":"
                + ts
                + "}"
                + "}",
            "{}");
    assertThat(one.timestamp()).isEqualTo(ts);
  }

  @Test
  void testBefore() {
    Fact one =
        Fact.of(
            "{"
                + "\"ns\":\"ns\","
                + "\"id\":\""
                + UUID.randomUUID()
                + "\","
                + "\"meta\":{ \"_ser\":1 }"
                + "}",
            "{}");
    Fact two =
        Fact.of(
            "{"
                + "\"ns\":\"ns\","
                + "\"id\":\""
                + UUID.randomUUID()
                + "\","
                + "\"meta\":{ \"_ser\":2 }"
                + "}",
            "{}");
    Fact three =
        Fact.of(
            "{"
                + "\"ns\":\"ns\","
                + "\"id\":\""
                + UUID.randomUUID()
                + "\","
                + "\"meta\":{ \"_ser\":3 }"
                + "}",
            "{}");
    assertTrue(one.before(two));
    assertTrue(two.before(three));
    assertTrue(one.before(three));
    assertFalse(one.before(one));
    assertFalse(two.before(one));
    assertFalse(three.before(one));
    assertFalse(three.before(two));
  }

  @Test
  void beforeFailsIfSerialUnknown() {
    Fact one =
        Fact.of(
            "{" + "\"ns\":\"ns\"," + "\"id\":\"" + UUID.randomUUID() + "\"," + "\"meta\":{ }" + "}",
            "{}");
    Fact two =
        Fact.of(
            "{"
                + "\"ns\":\"ns\","
                + "\"id\":\""
                + UUID.randomUUID()
                + "\","
                + "\"meta\":{ \"_ser\":2 }"
                + "}",
            "{}");

    assertThatThrownBy(
            () -> {
              one.before(two);
            })
        .isInstanceOf(IllegalStateException.class);

    assertThatThrownBy(
            () -> {
              two.before(one);
            })
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void testSerialUnset() {
    assertThrows(
        IllegalStateException.class,
        () ->
            Fact.of("{" + "\"ns\":\"ns\"," + "\"id\":\"" + UUID.randomUUID() + "\"" + "}", "{}")
                .serial());
  }

  @Test
  void testBuilderDefaults() {
    Fact f = Fact.builder().build("{\"a\":1}");
    assertEquals("default", f.ns());
    assertNotNull(f.id());
    assertEquals("{\"a\":1}", f.jsonPayload());
  }

  @Test
  void testBuilder() {
    UUID aggId1 = UUID.randomUUID();
    UUID aggId2 = UUID.randomUUID();
    UUID aggId3 = UUID.randomUUID();
    UUID factId = UUID.randomUUID();
    Fact f =
        Fact.builder()
            .ns("ns")
            .type("type")
            .aggId(aggId1)
            .aggId(aggId2)
            .aggId(aggId3)
            .setMeta("foo", "bar")
            .setMeta("buh", "bang")
            .id(factId)
            .build("{\"a\":2}");
    assertEquals("ns", f.ns());
    assertEquals("type", f.type());
    assertTrue(f.aggIds().contains(aggId1));
    assertTrue(f.aggIds().contains(aggId2));
    assertTrue(f.aggIds().contains(aggId3));
    assertFalse(f.aggIds().contains(factId));
    assertEquals("bar", f.header().meta().getFirst("foo"));
    assertEquals("bang", f.header().meta().getFirst("buh"));
    assertEquals("{\"a\":2}", f.jsonPayload());
  }

  @Test
  void testOfJsonNodeJsonNodeNull1() {
    assertThrows(NullPointerException.class, () -> Fact.of(null, Mockito.mock(JsonNode.class)));
  }

  @Test
  void testOfJsonNodeJsonNodeNull2() {
    assertThrows(NullPointerException.class, () -> Fact.of(Mockito.mock(JsonNode.class), null));
  }

  @Test
  void testOfJsonNodeJsonNodeNull() {
    assertThrows(NullPointerException.class, () -> Fact.of((JsonNode) null, null));
  }

  @Test
  void testOfJsonNode() {
    JsonNode payload = FactCastJson.newObjectNode();
    String headerString = "{\"id\":\"" + UUID.randomUUID() + "\",\"ns\":\"ns\"}";
    JsonNode header = FactCastJson.toObjectNode(headerString);
    assertEquals(headerString, Fact.of(header, payload).jsonHeader());
  }

  @Test
  void testBuildWithoutPayload() {
    Fact f = Fact.builder().ns("foo").buildWithoutPayload();
    assertThat(f.ns()).isEqualTo("foo");
    assertThat(f.jsonPayload()).isEqualTo("{}");
  }

  @Test
  void testMeta() {
    Fact f = Fact.builder().ns("foo").meta("a", "1").buildWithoutPayload();
    assertThat(f.meta("a")).isEqualTo("1");
  }

  @Test
  void testTypeEmpty() {
    assertThrows(IllegalArgumentException.class, () -> Fact.builder().type(""));
  }

  @Test
  void testNsEmpty() {
    assertThrows(IllegalArgumentException.class, () -> Fact.builder().ns(""));
  }

  @Test
  void testSerialMustExistInMeta() {
    assertThrows(
        IllegalStateException.class,
        () -> {
          Fact f = Fact.builder().ns("a").buildWithoutPayload();
          f.serial();
        });
  }

  @Test
  void testOfJsonNodeJsonNode() {
    ObjectNode h = FactCastJson.toObjectNode("{\"id\":\"" + new UUID(0, 1) + "\",\"ns\":\"foo\"}");
    ObjectNode p = FactCastJson.toObjectNode("{}");
    Fact f = Fact.of(h, p);
    assertThat(f.ns()).isEqualTo("foo");
    assertThat(f.id()).isEqualTo(new UUID(0, 1));
  }

  @Test
  void testEmptyPayload() {
    assertThat(Fact.builder().build("").jsonPayload()).isEqualTo("{}");
    assertThat(Fact.builder().build("   ").jsonPayload()).isEqualTo("{}");
  }

  @Test
  void buildFromEventObject() {
    UUID userId = UUID.randomUUID();
    UUID factId = UUID.randomUUID();
    String name = "Peter";
    SomeUserCreatedEvent event = new SomeUserCreatedEvent(userId, name);
    Fact.FactFromEventBuilder b = Fact.buildFrom(event);
    assertThat(b).isNotNull();

    Fact f = b.serial(12).id(factId).build();

    assertThat(f.serial()).isEqualTo(12);
    assertThat(f.id()).isEqualTo(factId);
    assertThat(f.jsonPayload()).isEqualTo(FactCastJson.writeValueAsString(event));
    assertThat(f.aggIds()).hasSize(1).containsExactly(userId);
    assertThat(f.ns()).isEqualTo("test");
    assertThat(f.type()).isEqualTo("SomeUserCreatedEvent");
    assertThat(f.version()).isZero();
  }

  @Test
  void buildFromEventObjectWIthVersion() {
    UUID userId = UUID.randomUUID();
    UUID factId = UUID.randomUUID();
    String name = "Peter";
    SomeUserCreatedEvent event = new SomeUserCreatedEvent(userId, name);
    Fact.FactFromEventBuilder b = Fact.buildFrom(event);
    assertThat(b).isNotNull();

    Fact f =
        b.serial(12).id(factId).version(7).meta("key", "value").meta("key", "otherValue").build();

    assertThat(f.header().serial()).isEqualTo(12);
    assertThat(f.id()).isEqualTo(factId);
    assertThat(f.jsonPayload()).isEqualTo(FactCastJson.writeValueAsString(event));
    assertThat(f.aggIds()).hasSize(1).containsExactly(userId);
    assertThat(f.ns()).isEqualTo("test");
    assertThat(f.type()).isEqualTo("SomeUserCreatedEvent");
    assertThat(f.version()).isEqualTo(7);
    assertThat(f.header().meta().getFirst("key")).isEqualTo("otherValue"); // overridden
    assertThat(f.header().meta().getAll("key")).hasSize(1); // overridden
  }

  @Specification(ns = "test")
  private static class MyEventObject implements EventObject {
    @Override
    public Set<UUID> aggregateIds() {
      return Sets.newHashSet(UUID.randomUUID());
    }
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @Specification(ns = "test")
  public class SomeUserCreatedEvent implements EventObject {
    UUID aggregateId;

    String userName;

    @Override
    public Set<UUID> aggregateIds() {
      return Sets.newHashSet(aggregateId);
    }
  }

  @SuppressWarnings("deprecation")
  @Test
  void testMultiMapCompatibility() {
    Fact fact = Fact.builder().meta("foo", "bar").meta("foo", "baz").buildWithoutPayload();
    MetaMap meta = fact.header().meta();
    Assertions.assertThat(meta).isNotNull();
    // check that set is being used rather than add
    Assertions.assertThat(meta.getAll("foo")).hasSize(1);
    Assertions.assertThat(meta.getFirst("foo")).isEqualTo("baz");
  }

  @Test
  void testMultiMapSet() {
    Fact fact = Fact.builder().setMeta("foo", "bar").setMeta("foo", "baz").buildWithoutPayload();
    MetaMap meta = fact.header().meta();
    Assertions.assertThat(meta).isNotNull();
    // check that set is being used rather than add
    Assertions.assertThat(meta.getAll("foo")).hasSize(1);
    Assertions.assertThat(meta.getFirst("foo")).isEqualTo("baz");
  }

  @Test
  void testMultiMapAdd() {
    Fact fact = Fact.builder().addMeta("foo", "bar").addMeta("foo", "baz").buildWithoutPayload();
    MetaMap meta = fact.header().meta();
    Assertions.assertThat(meta).isNotNull();
    // check that set is being used rather than add
    Assertions.assertThat(meta.getAll("foo")).hasSize(2);
    Assertions.assertThat(meta.getAll("foo")).containsExactly("bar", "baz");
  }

  @Test
  void testVersion() {
    Fact fact = Fact.builder().version(12).buildWithoutPayload();
    Assertions.assertThat(fact.version()).isEqualTo(12);
  }

  @Test
  void rejectsVersionOutOfBounds() {
    Fact.Builder builder = Fact.builder();
    assertThatThrownBy(
            () -> {
              builder.version(0);
            })
        .isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(
            () -> {
              builder.version(-1);
            })
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void usesSerializer() {
    @NonNull EventObject event = new MyEventObject();
    String expectedJson = "{\"x\":¸\"y\"}";

    @NonNull
    EventSerializer mySerializer =
        new EventSerializer() {
          @Override
          public <T extends EventObject> T deserialize(Class<T> targetClass, String json) {
            return null;
          }

          @Override
          public <T extends EventObject> String serialize(T pojo) {
            return expectedJson;
          }
        };
    Fact.FactFromEventBuilder builder = Fact.buildFrom(event).using(mySerializer);
    Assertions.assertThat(builder.build().jsonPayload()).isEqualTo(expectedJson);
  }
}
