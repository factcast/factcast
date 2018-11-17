package org.factcast.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.UUID;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonParseException;

public class DefaultFact0Test {

    @Test
    public void testNullHeader() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            DefaultFact.of("{}", null);
        });
    }

    @Test
    public void testNullPayload() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            DefaultFact.of(null, "{}");
        });
    }

    @Test
    public void testNullContracts() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            DefaultFact.of(null, null);
        });
    }

    @Test
    public void testUnparsableHeader() {
        Assertions.assertThrows(JsonParseException.class, () -> {
            DefaultFact.of("not json at all", "{}");
        });
    }

    @Test
    public void testNoId() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            DefaultFact.of("{\"ns\":\"default\"}", "{}");
        });
    }

    @Test
    public void testIdNotUUID() {
        Assertions.assertThrows(IOException.class, () -> {
            DefaultFact.of("{\"id\":\"buh\",\"ns\":\"default\"}", "{}");
        });
    }

    @Test
    public void testValidFact() {
        DefaultFact.of("{\"id\":\"" + UUID.randomUUID() + "\",\"ns\":\"default\"}", "{}");
    }

    @Test
    public void testMetaDeser() {
        Fact f = DefaultFact.of("{\"id\":\"" + UUID.randomUUID()
                + "\",\"ns\":\"default\",\"meta\":{\"foo\":7}}", "{}");
        assertEquals("7", f.meta("foo"));
    }

    @Test
    public void testExternalization() throws Exception {
        Fact f = DefaultFact.of("{\"id\":\"" + UUID.randomUUID()
                + "\",\"ns\":\"default\",\"meta\":{\"foo\":7}}", "{}");
        Fact copy = copyBySerialization(f);
        assertEquals(f, copy);
    }

    @SuppressWarnings("unchecked")
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
    public void testJsonHeader() {
        final UUID id = UUID.randomUUID();
        final UUID aid = UUID.randomUUID();
        final String header = "{\"id\":\"" + id
                + "\",\"ns\":\"narf\",\"type\":\"foo\",\"aggIds\":[\"" + aid
                + "\"],\"meta\":{\"foo\":7}}";
        Fact f = DefaultFact.of(header, "{}");
        assertSame(header, f.jsonHeader());
    }

    @Test
    public void testEqualityBasedOnId() {
        final UUID id = UUID.randomUUID();
        final UUID aid = UUID.randomUUID();
        final String header = "{\"id\":\"" + id
                + "\",\"ns\":\"narf\",\"type\":\"foo\",\"aggIds\":[\"" + aid
                + "\"],\"meta\":{\"foo\":7}}";
        Fact f = DefaultFact.of(header, "{}");
        Fact f2 = DefaultFact.of("{\"ns\":\"ns\",\"id\":\"" + id + "\"}", "{}");
        Fact f3 = DefaultFact.of("{\"ns\":\"ns\",\"id\":\"" + aid + "\"}", "{}");
        assertEquals(f, f);
        assertEquals(f, f2);
        assertNotEquals(f, f3);
    }

    @Test
    public void testCopyAttributes() throws Exception {
        final UUID id = UUID.randomUUID();
        final UUID aid = UUID.randomUUID();
        Fact f = DefaultFact.of("{\"id\":\"" + id
                + "\",\"ns\":\"narf\",\"type\":\"foo\",\"aggIds\":[\"" + aid
                + "\"],\"meta\":{\"foo\":7}}", "{}");
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
    public void testEqualityMustBeBasedOnIDOnly() {
        UUID id = UUID.randomUUID();
        Fact f1 = DefaultFact.of("{\"id\":\"" + id
                + "\",\"ns\":\"narf\",\"type\":\"foo\",\"aggIds\":[\"" + UUID.randomUUID()
                + "\"],\"meta\":{\"foo\":7}}", "{}");
        Fact f2 = DefaultFact.of("{\"id\":\"" + id
                + "\",\"ns\":\"poit\",\"type\":\"bar\",\"aggIds\":[\"" + UUID.randomUUID()
                + "\"],\"meta\":{\"foo\":7}}", "{}");
        assertEquals(f1, f2);
    }

    @Test
    public void testOfNoId() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            Fact.of("{\"ns\":\"narf\"}", "{}");
        });
    }

    @Test
    public void testOfNoNs() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            Fact.of("{\"id\":\"" + UUID.randomUUID() + "\"}", "{}");
        });
    }

    @Test
    public void testOfEmptyNs() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            Fact.of("{\"id\":\"" + UUID.randomUUID() + "\",\"ns\":\"\"}", "{}");
        });
    }
}
