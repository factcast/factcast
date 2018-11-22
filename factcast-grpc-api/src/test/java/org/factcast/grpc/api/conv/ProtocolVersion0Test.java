package org.factcast.grpc.api.conv;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class ProtocolVersion0Test {

    @Test
    void testToString() {
        assertEquals("3.1.2", ProtocolVersion.of(3, 1, 2).toString());
    }
}
