/*
 * Copyright © 2018 Mercateo AG (http://www.mercateo.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
