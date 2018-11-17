package org.factcast.grpc.api.conv;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class ProtocolVersionTest {

    @Test
    public void testIsCompatibleTo() {
        assertTrue(ProtocolVersion.of(1, 0, 0).isCompatibleTo(ProtocolVersion.of(1, 0, 0)));
        assertTrue(ProtocolVersion.of(1, 0, 0).isCompatibleTo(ProtocolVersion.of(1, 0, 1)));
        assertTrue(ProtocolVersion.of(1, 0, 0).isCompatibleTo(ProtocolVersion.of(1, 1, 0)));
        assertFalse(ProtocolVersion.of(1, 1, 0).isCompatibleTo(ProtocolVersion.of(1, 0, 0)));
        assertFalse(ProtocolVersion.of(1, 1, 0).isCompatibleTo(ProtocolVersion.of(2, 0, 0)));
    }
}
