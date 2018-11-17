package org.factcast.grpc.api;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class Capabilities0Test {

    @Test
    public void testToString() {
        assertEquals("org.factcast.grpc.api.Capabilities.CODEC_LZ4", Capabilities.CODEC_LZ4
                .toString());
    }
}
