package org.factcast.client.grpc;

import java.util.UUID;

import org.junit.Test;

public class IdOnlyFactTest {

    @Test(expected = NullPointerException.class)
    public void testIdNonNull() throws Exception {
        new IdOnlyFact(null);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testNsUnsupported() throws Exception {
        new IdOnlyFact(UUID.randomUUID()).ns();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testaggIdUnsupported() throws Exception {
        new IdOnlyFact(UUID.randomUUID()).aggId();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testtypeUnsupported() throws Exception {
        new IdOnlyFact(UUID.randomUUID()).type();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testHeaderUnsupported() throws Exception {
        new IdOnlyFact(UUID.randomUUID()).jsonHeader();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testPayloadUnsupported() throws Exception {
        new IdOnlyFact(UUID.randomUUID()).jsonPayload();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testMetaUnsupported() throws Exception {
        new IdOnlyFact(UUID.randomUUID()).meta("foo");
    }
}
