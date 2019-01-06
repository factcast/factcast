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
package org.factcast.core;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

import org.factcast.core.util.FactCastJson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.fasterxml.jackson.databind.JsonNode;

public class FactTest {

    @Test
    void testOfNull1() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            Fact.of(null, "");
        });
    }

    @Test
    void testOfNull2() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            Fact.of("", null);
        });
    }

    @Test
    void testOfNull() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            Fact.of((String) null, null);
        });
    }

    @Test
    void testOf() {
        TestFact f = new TestFact();
        Fact f2 = Fact.of(f.jsonHeader(), f.jsonPayload());
        assertEquals(f.id(), f2.id());
    }

    @Test
    void testBefore() {
        Fact one = Fact.of(
                "{" + "\"ns\":\"ns\"," + "\"id\":\"" + UUID.randomUUID() + "\","
                        + "\"meta\":{ \"_ser\":1 }" + "}",
                "{}");
        Fact two = Fact.of(
                "{" + "\"ns\":\"ns\"," + "\"id\":\"" + UUID.randomUUID() + "\","
                        + "\"meta\":{ \"_ser\":2 }" + "}",
                "{}");
        Fact three = Fact.of(
                "{" + "\"ns\":\"ns\"," + "\"id\":\"" + UUID.randomUUID() + "\","
                        + "\"meta\":{ \"_ser\":3 }" + "}",
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
    void testSerialUnset() {
        Assertions.assertThrows(IllegalStateException.class, () -> {
            Fact.of("{" + "\"ns\":\"ns\"," + "\"id\":\"" + UUID.randomUUID() + "\"" + "}", "{}")
                    .serial();
        });
    }

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
        Fact f = Fact.builder()
                .ns("ns")
                .type("type")
                .aggId(aggId1)
                .aggId(aggId2)
                .aggId(aggId3)
                .meta("foo", "bar")
                .meta("buh", "bang")
                .id(factId)
                .build("{\"a\":2}");
        assertEquals("ns", f.ns());
        assertEquals("type", f.type());
        assertTrue(f.aggIds().contains(aggId1));
        assertTrue(f.aggIds().contains(aggId2));
        assertTrue(f.aggIds().contains(aggId3));
        assertFalse(f.aggIds().contains(factId));
        assertEquals("bar", f.meta("foo"));
        assertEquals("bang", f.meta("buh"));
        assertEquals("{\"a\":2}", f.jsonPayload());
    }

    @Test
    void testBuilderNullContracts() {

        assertThrows(NullPointerException.class, () -> {
            Fact.builder().ns(null);
        });

        assertThrows(NullPointerException.class, () -> {
            Fact.builder().type(null);
        });
        assertThrows(NullPointerException.class, () -> {
            Fact.builder().aggId(null);
        });
        assertThrows(NullPointerException.class, () -> {
            Fact.builder().meta(null, "x");
        });
        assertThrows(NullPointerException.class, () -> {
            Fact.builder().id(null);
        });
        assertThrows(NullPointerException.class, () -> {
            Fact.builder().build(null);
        });

        Fact.builder().meta("x", null).build("{}");

    }

    @Test
    void testOfJsonNodeJsonNodeNull1() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            Fact.of(null, Mockito.mock(JsonNode.class));
        });
    }

    @Test
    void testOfJsonNodeJsonNodeNull2() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            Fact.of(Mockito.mock(JsonNode.class), null);
        });
    }

    @Test
    void testOfJsonNodeJsonNodeNull() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            Fact.of((JsonNode) null, null);
        });
    }

    @Test
    void testOfJsonNode() {
        JsonNode payload = FactCastJson.newObjectNode();
        String headerString = "{\"id\":\"" + UUID.randomUUID() + "\",\"ns\":\"ns\"}";
        JsonNode header = FactCastJson.toObjectNode(headerString);
        assertEquals(headerString, Fact.of(header, payload).jsonHeader());
    }
}
