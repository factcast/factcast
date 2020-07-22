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

import java.io.*;
import java.util.UUID;

import org.factcast.core.util.FactCastJson.Encoder;
import org.junit.jupiter.api.*;

public class BinaryFactTest {

    @Test
    void testNullHeader() {
        Assertions.assertThrows(NullPointerException.class, () -> Fact.of(Encoder.MSGPACK, "{}"
                .getBytes(), null));
    }

    @Test
    void testNullPayload() {
        Assertions.assertThrows(NullPointerException.class, () -> Fact.of(Encoder.MSGPACK, null,
                "{}".getBytes()));
    }

    @Test
    void testNullContracts() {
        Assertions.assertThrows(NullPointerException.class, () -> Fact.of(Encoder.MSGPACK,
                (byte[]) null, null));
    }

    @Test
    void testUnparseableHeader() {
        Assertions.assertThrows(IOException.class, () -> Fact.of(Encoder.MSGPACK, "broken"
                .getBytes(),
                TestHelper.json2msgpack("{}")));
    }

    @Test
    void testNoId() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> Fact.of(Encoder.MSGPACK,
                TestHelper.json2msgpack("{\"ns\":\"default\"}"), TestHelper.json2msgpack("{}")));
    }

    @Test
    void testIdNotUUID() {
        Assertions.assertThrows(IOException.class, () -> Fact.of(Encoder.MSGPACK,
                TestHelper.json2msgpack("{\"id\":\"buh\",\"ns\":\"default\"}"), TestHelper
                        .json2msgpack("{}")));
    }

    @Test
    void testValidFact() {
        Fact.of(Encoder.MSGPACK, TestHelper.json2msgpack("{\"id\":\"" + UUID.randomUUID()
                + "\",\"ns\":\"default\"}"), TestHelper.json2msgpack("{}"));
    }

    @Test
    void testMetaDeser() {
        Fact f = Fact.of(Encoder.MSGPACK, TestHelper.json2msgpack("{\"id\":\"" + UUID.randomUUID()
                + "\",\"ns\":\"default\",\"meta\":{\"foo\":\"7\"}}"), TestHelper.json2msgpack(
                        "{}"));
        assertEquals("7", f.meta("foo"));
    }

    @Test
    void testExternalization() throws Exception {
        Fact f = Fact.of(Encoder.MSGPACK, TestHelper.json2msgpack("{\"id\":\"" + UUID.randomUUID()
                + "\",\"ns\":\"default\",\"meta\":{\"foo\":\"7\"}}"), TestHelper.json2msgpack(
                        "{}"));
        Fact copy = copyBySerialization(f);
        assertEquals(f, copy);
    }

    private <T> T copyBySerialization(T f) throws IOException, ClassNotFoundException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        ObjectOutputStream objectOutputStream = new ObjectOutputStream(os);
        objectOutputStream.writeObject(f);
        objectOutputStream.close();
        try (ByteArrayInputStream is = new ByteArrayInputStream(os.toByteArray());) {
            ObjectInputStream objectInputStream = new ObjectInputStream(is);
            return (T) objectInputStream.readObject();
        }
    }

    @Test
    void testJsonHeader() {
        final UUID id = UUID.randomUUID();
        final UUID aid = UUID.randomUUID();
        final String header = "{\"id\":\"" + id
                + "\",\"ns\":\"narf\",\"type\":\"foo\",\"version\":0,\"aggIds\":[\"" + aid
                + "\"],\"meta\":{\"foo\":\"7\"}}";
        Fact f = Fact.of(Encoder.MSGPACK, TestHelper.json2msgpack(header), TestHelper.json2msgpack(
                "{}"));
        assertEquals(header, f.jsonHeader());
    }

    @Test
    void testEqualityBasedOnId() {
        final UUID id = UUID.randomUUID();
        final UUID aid = UUID.randomUUID();
        final String header = "{\"id\":\"" + id
                + "\",\"ns\":\"narf\",\"type\":\"foo\",\"aggIds\":[\"" + aid
                + "\"],\"meta\":{\"foo\":\"7\"}}";
        Fact f = Fact.of(Encoder.MSGPACK, TestHelper.json2msgpack(header), TestHelper.json2msgpack(
                "{}"));
        Fact f2 = Fact.of(Encoder.MSGPACK, TestHelper.json2msgpack("{\"ns\":\"ns\",\"id\":\"" + id
                + "\"}"),
                TestHelper.json2msgpack("{}"));
        Fact f3 = Fact.of(Encoder.MSGPACK, TestHelper.json2msgpack("{\"ns\":\"ns\",\"id\":\"" + aid
                + "\"}"),
                TestHelper.json2msgpack("{}"));
        assertEquals(f, f);
        assertEquals(f, f2);
        assertNotEquals(f, f3);
    }

    @Test
    void testCopyAttributes() throws Exception {
        final UUID id = UUID.randomUUID();
        final UUID aid = UUID.randomUUID();
        Fact f = Fact.of(Encoder.MSGPACK, TestHelper.json2msgpack("{\"id\":\"" + id
                + "\",\"ns\":\"narf\",\"type\":\"foo\",\"aggIds\":[\"" + aid
                + "\"],\"meta\":{\"foo\":\"7\"}}"), TestHelper.json2msgpack("{}"));
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
        Fact f1 = Fact.of("{\"id\":\"" + id
                + "\",\"ns\":\"narf\",\"type\":\"foo\",\"aggIds\":[\""
                + UUID.randomUUID() + "\"],\"meta\":{\"foo\":7}}", "{}");
        Fact f2 = Fact.of(Encoder.MSGPACK, TestHelper.json2msgpack("{\"id\":\"" + id
                + "\",\"ns\":\"poit\",\"type\":\"bar\",\"aggIds\":[\""
                + UUID.randomUUID() + "\"],\"meta\":{\"foo\":\"7\"}}"), TestHelper.json2msgpack(
                        "{}"));
        assertEquals(f1, f2);
    }

    @Test
    void testOfNoId() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> Fact.of(Encoder.MSGPACK,
                TestHelper
                        .json2msgpack("{\"ns\":\"narf\"}"),
                TestHelper.json2msgpack("{}")));
    }

    @Test
    void testOfNoNs() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> Fact.of(Encoder.MSGPACK,
                TestHelper
                        .json2msgpack("{\"id\":\"" + UUID
                                .randomUUID() + "\"}"), TestHelper.json2msgpack("{}")));
    }

    @Test
    void testOfEmptyNs() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> Fact.of(Encoder.MSGPACK,
                TestHelper
                        .json2msgpack("{\"id\":\"" + UUID
                                .randomUUID() + "\",\"ns\":\"\"}"), TestHelper.json2msgpack("{}")));
    }

    @Test
    void testOfInvalidVersion() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> Fact.of(Encoder.MSGPACK,
                TestHelper
                        .json2msgpack("{\"id\":\"" + UUID
                                .randomUUID() + "\",\"version\":\"" + -1
                                + "\",\"ns\":\"ns\"}"), TestHelper.json2msgpack("{}")));
    }

    @Test
    void testVersionPrevails() {
        assertEquals(7, Fact.of(Encoder.MSGPACK, TestHelper.json2msgpack("{\"id\":\"" + UUID
                .randomUUID()
                + "\",\"version\":\"" + 7
                + "\",\"ns\":\"ns\"}"), TestHelper.json2msgpack("{}"))
                .version());

    }
}
