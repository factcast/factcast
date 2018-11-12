package org.factcast.grpc.api;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class Capabilities0Test {

    @Test
    public void testToString() {
        assertEquals("org.factcast.grpc.api.Capabilities.CODEC_LZ4", Capabilities.CODEC_LZ4
                .toString());
    }

}
