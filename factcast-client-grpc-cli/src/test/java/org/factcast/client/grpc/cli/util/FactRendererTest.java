package org.factcast.client.grpc.cli.util;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

import org.factcast.client.grpc.cli.util.Parser.Options;
import org.factcast.core.Fact;
import org.junit.jupiter.api.Test;

public class FactRendererTest {

    Fact f = Fact.builder()
            .id(new UUID(0L, 0L))
            .ns("ns")
            .aggId(new UUID(0L, 1L))
            .meta("foo", "bar")
            .type("type")
            .build("{\"some\":\"json\"}");

    @Test
    void testRender() throws Exception {
        FactRenderer uut = new FactRenderer(new Options());
        assertEquals("Fact: id=00000000-0000-0000-0000-000000000000\n"
                + "	header: {\"id\":\"00000000-0000-0000-0000-000000000000\",\"ns\":\"ns\",\"type\":\"type\",\"aggIds\":[\"00000000-0000-0000-0000-000000000001\"],\"meta\":{\"foo\":\"bar\"}}\n"
                + "	payload: {\"some\":\"json\"}\n" + "\n", uut.render(f));
    }

    @Test
    void testRenderPretty() throws Exception {
        Options options = new Options() {
            @Override
            public boolean pretty() {
                return true;
            }
        };

        FactRenderer uut = new FactRenderer(options);
        assertEquals(
                "Fact: id=00000000-0000-0000-0000-000000000000\n" + "	header: {\n"
                        + "		  \"id\" : \"00000000-0000-0000-0000-000000000000\",\n"
                        + "		  \"ns\" : \"ns\",\n"
                        + "		  \"type\" : \"type\",\n"
                        + "		  \"aggIds\" : [ \"00000000-0000-0000-0000-000000000001\" ],\n"
                        + "		  \"meta\" : {\n" + "		    \"foo\" : \"bar\"\n"
                        + "		  }\n" + "		}\n"
                        + "	payload: {\n" + "		  \"some\" : \"json\"\n" + "		}\n" + "\n"
                        + "",
                uut.render(f));
    }
}
