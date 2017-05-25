package org.factcast.core;

import java.util.UUID;

import org.junit.Test;

public class IdOnlyFact0Test {

    @Test(expected = UnsupportedOperationException.class)
    public void testNs() throws Exception {
        new IdOnlyFact(UUID.randomUUID()).ns();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testType() throws Exception {
        new IdOnlyFact(UUID.randomUUID()).type();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testAggIds() throws Exception {
        new IdOnlyFact(UUID.randomUUID()).aggIds();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testJsonHeader() throws Exception {
        new IdOnlyFact(UUID.randomUUID()).jsonHeader();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testJsonPayload() throws Exception {
        new IdOnlyFact(UUID.randomUUID()).jsonPayload();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testMeta() throws Exception {
        new IdOnlyFact(UUID.randomUUID()).meta("");
    }

    @Test(expected = NullPointerException.class)
    public void testNullId() throws Exception {
        new IdOnlyFact(null);
    }
}
