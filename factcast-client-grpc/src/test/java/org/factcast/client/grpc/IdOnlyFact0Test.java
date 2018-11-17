package org.factcast.client.grpc;

import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.UUID;

import org.factcast.core.IdOnlyFact;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class IdOnlyFact0Test {

    @Test
    public void testIdNonNull() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            new IdOnlyFact(null);
        });
    }

    @Test
    public void testNsUnsupported() {
        Assertions.assertThrows(UnsupportedOperationException.class, () -> {
            new IdOnlyFact(UUID.randomUUID()).ns();
        });
    }

    @Test
    public void testaggIdUnsupported() {
        Assertions.assertThrows(UnsupportedOperationException.class, () -> {
            new IdOnlyFact(UUID.randomUUID()).aggIds();
        });
    }

    @Test
    public void testtypeUnsupported() {
        Assertions.assertThrows(UnsupportedOperationException.class, () -> {
            new IdOnlyFact(UUID.randomUUID()).type();
        });
    }

    @Test
    public void testHeaderUnsupported() {
        Assertions.assertThrows(UnsupportedOperationException.class, () -> {
            new IdOnlyFact(UUID.randomUUID()).jsonHeader();
        });
    }

    @Test
    public void testPayloadUnsupported() {
        Assertions.assertThrows(UnsupportedOperationException.class, () -> {
            new IdOnlyFact(UUID.randomUUID()).jsonPayload();
        });
    }

    @Test
    public void testMetaUnsupported() {
        Assertions.assertThrows(UnsupportedOperationException.class, () -> {
            new IdOnlyFact(UUID.randomUUID()).meta("foo");
        });
    }

    @Test
    public void testId() {
        UUID id = UUID.randomUUID();
        assertSame(id, new IdOnlyFact(id).id());
    }
}
