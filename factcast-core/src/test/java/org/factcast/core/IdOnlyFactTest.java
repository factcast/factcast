package org.factcast.core;

import java.util.UUID;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class IdOnlyFactTest {

    @Test
    void testNs() {
        Assertions.assertThrows(UnsupportedOperationException.class, () -> {
            new IdOnlyFact(UUID.randomUUID()).ns();
        });
    }

    @Test
    void testType() {
        Assertions.assertThrows(UnsupportedOperationException.class, () -> {
            new IdOnlyFact(UUID.randomUUID()).type();
        });
    }

    @Test
    void testAggIds() {
        Assertions.assertThrows(UnsupportedOperationException.class, () -> {
            new IdOnlyFact(UUID.randomUUID()).aggIds();
        });
    }

    @Test
    void testJsonHeader() {
        Assertions.assertThrows(UnsupportedOperationException.class, () -> {
            new IdOnlyFact(UUID.randomUUID()).jsonHeader();
        });
    }

    @Test
    void testJsonPayload() {
        Assertions.assertThrows(UnsupportedOperationException.class, () -> {
            new IdOnlyFact(UUID.randomUUID()).jsonPayload();
        });
    }

    @Test
    void testMeta() {
        Assertions.assertThrows(UnsupportedOperationException.class, () -> {
            new IdOnlyFact(UUID.randomUUID()).meta("");
        });
    }

    @Test
    void testNullId() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            new IdOnlyFact(null);
        });
    }
}
