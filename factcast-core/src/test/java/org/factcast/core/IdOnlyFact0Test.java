package org.factcast.core;

import java.util.UUID;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class IdOnlyFact0Test {

    @Test
    public void testNs() {
        Assertions.assertThrows(UnsupportedOperationException.class, () -> {
            new IdOnlyFact(UUID.randomUUID()).ns();
        });
    }

    @Test
    public void testType() {
        Assertions.assertThrows(UnsupportedOperationException.class, () -> {
            new IdOnlyFact(UUID.randomUUID()).type();
        });
    }

    @Test
    public void testAggIds() {
        Assertions.assertThrows(UnsupportedOperationException.class, () -> {
            new IdOnlyFact(UUID.randomUUID()).aggIds();
        });
    }

    @Test
    public void testJsonHeader() {
        Assertions.assertThrows(UnsupportedOperationException.class, () -> {
            new IdOnlyFact(UUID.randomUUID()).jsonHeader();
        });
    }

    @Test
    public void testJsonPayload() {
        Assertions.assertThrows(UnsupportedOperationException.class, () -> {
            new IdOnlyFact(UUID.randomUUID()).jsonPayload();
        });
    }

    @Test
    public void testMeta() {
        Assertions.assertThrows(UnsupportedOperationException.class, () -> {
            new IdOnlyFact(UUID.randomUUID()).meta("");
        });
    }

    @Test
    public void testNullId() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            new IdOnlyFact(null);
        });
    }
}
