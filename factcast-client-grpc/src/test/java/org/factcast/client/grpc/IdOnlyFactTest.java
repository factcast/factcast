package org.factcast.client.grpc;

import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.UUID;

import org.factcast.core.IdOnlyFact;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class IdOnlyFactTest {

    @Test
    void testIdNonNull() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            new IdOnlyFact(null);
        });
    }

    @Test
    void testNsUnsupported() {
        Assertions.assertThrows(UnsupportedOperationException.class, () -> {
            new IdOnlyFact(UUID.randomUUID()).ns();
        });
    }

    @Test
    void testaggIdUnsupported() {
        Assertions.assertThrows(UnsupportedOperationException.class, () -> {
            new IdOnlyFact(UUID.randomUUID()).aggIds();
        });
    }

    @Test
    void testtypeUnsupported() {
        Assertions.assertThrows(UnsupportedOperationException.class, () -> {
            new IdOnlyFact(UUID.randomUUID()).type();
        });
    }

    @Test
    void testHeaderUnsupported() {
        Assertions.assertThrows(UnsupportedOperationException.class, () -> {
            new IdOnlyFact(UUID.randomUUID()).jsonHeader();
        });
    }

    @Test
    void testPayloadUnsupported() {
        Assertions.assertThrows(UnsupportedOperationException.class, () -> {
            new IdOnlyFact(UUID.randomUUID()).jsonPayload();
        });
    }

    @Test
    void testMetaUnsupported() {
        Assertions.assertThrows(UnsupportedOperationException.class, () -> {
            new IdOnlyFact(UUID.randomUUID()).meta("foo");
        });
    }

    @Test
    void testId() {
        UUID id = UUID.randomUUID();
        assertSame(id, new IdOnlyFact(id).id());
    }
}
