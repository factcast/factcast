package org.factcast.core;

import java.util.UUID;

import org.junit.Test;

public class IdOnlyFact0Test {

    @Test(expected = UnsupportedOperationException.class)
    public void testNs() {
        new IdOnlyFact(UUID.randomUUID()).ns();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testType() {
        new IdOnlyFact(UUID.randomUUID()).type();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testAggIds() {
        new IdOnlyFact(UUID.randomUUID()).aggIds();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testJsonHeader() {
        new IdOnlyFact(UUID.randomUUID()).jsonHeader();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testJsonPayload() {
        new IdOnlyFact(UUID.randomUUID()).jsonPayload();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testMeta() {
        new IdOnlyFact(UUID.randomUUID()).meta("");
    }

    @Test(expected = NullPointerException.class)
    public void testNullId() {
        new IdOnlyFact(null);
    }
}
