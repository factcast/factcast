package org.factcast.core;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.UUID;

import org.junit.Test;

import com.fasterxml.jackson.core.JsonParseException;

public class DefaultFactTest {

    @Test(expected = NullPointerException.class)
    public void testNullHeader() throws Exception {
        DefaultFact.of("{}", null);
    }

    @Test(expected = NullPointerException.class)
    public void testNullPayload() throws Exception {
        DefaultFact.of(null, "{}");
    }

    @Test(expected = NullPointerException.class)
    public void testNullContracts() throws Exception {
        DefaultFact.of(null, null);
    }

    @Test(expected = JsonParseException.class)
    public void testUnparsableHeader() throws Exception {
        DefaultFact.of("not json at all", "{}");
    }

    @Test(expected = IOException.class)
    public void testNoId() throws Exception {
        DefaultFact.of("{\"ns\":\"default\"}", "{}");
    }

    @Test(expected = IOException.class)
    public void testIdNotUUID() throws Exception {
        DefaultFact.of("{\"id\":\"buh\",\"ns\":\"default\"}", "{}");
    }

    @Test
    public void testValidFact() throws Exception {
        DefaultFact.of("{\"id\":\"" + UUID.randomUUID() + "\",\"ns\":\"default\"}", "{}");
    }

    @Test
    public void testMetaDeser() throws Exception {
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
    public void testJsonHeader() throws Exception {
        final UUID id = UUID.randomUUID();
        final UUID aid = UUID.randomUUID();
        final String header = "{\"id\":\"" + id
                + "\",\"ns\":\"narf\",\"type\":\"foo\",\"aggId\":[\"" + aid
                + "\"],\"meta\":{\"foo\":7}}";
        Fact f = DefaultFact.of(header, "{}");

        assertSame(header, f.jsonHeader());
    }

    @Test
    public void testEqualityBasedOnId() throws Exception {
        final UUID id = UUID.randomUUID();
        final UUID aid = UUID.randomUUID();
        final String header = "{\"id\":\"" + id
                + "\",\"ns\":\"narf\",\"type\":\"foo\",\"aggId\":[\"" + aid
                + "\"],\"meta\":{\"foo\":7}}";

        Fact f = DefaultFact.of(header, "{}");
        Fact f2 = DefaultFact.of("{\"id\":\"" + id + "\"}", "{}");
        Fact f3 = DefaultFact.of("{\"id\":\"" + aid + "\"}", "{}");

        assertEquals(f, f);
        assertEquals(f, f2);
        assertNotEquals(f, f3);
    }

    @Test
    public void testCopyAttributes() throws Exception {
        final UUID id = UUID.randomUUID();
        final UUID aid = UUID.randomUUID();
        Fact f = DefaultFact.of("{\"id\":\"" + id
                + "\",\"ns\":\"narf\",\"type\":\"foo\",\"aggId\":[\"" + aid
                + "\"],\"meta\":{\"foo\":7}}", "{}");

        Fact copy = copyBySerialization(f);

        assertNotSame(f.id(), copy.id());
        assertNotSame(f.ns(), copy.ns());
        assertNotSame(f.type(), copy.type());
        assertNotSame(f.aggId(), copy.aggId());
        assertNotSame(f.meta("foo"), copy.meta("foo"));

        assertEquals(f.id(), copy.id());
        assertEquals(f.ns(), copy.ns());
        assertEquals(f.type(), copy.type());
        assertEquals(f.aggId(), copy.aggId());
        assertEquals(f.meta("foo"), copy.meta("foo"));

        assertEquals(f.jsonPayload(), copy.jsonPayload());
    }

    @Test
    public void testEqualityMustBeBasedOnIDOnly() throws Exception {

        UUID id = UUID.randomUUID();

        Fact f1 = DefaultFact.of("{\"id\":\"" + id
                + "\",\"ns\":\"narf\",\"type\":\"foo\",\"aggId\":[\"" + UUID.randomUUID()
                + "\"],\"meta\":{\"foo\":7}}", "{}");

        Fact f2 = DefaultFact.of("{\"id\":\"" + id
                + "\",\"ns\":\"poit\",\"type\":\"bar\",\"aggId\":[\"" + UUID.randomUUID()
                + "\"],\"meta\":{\"foo\":7}}", "{}");

        assertEquals(f1, f2);

    }
}
