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
package org.factcast.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

public class FactHeaderTest {

    @Test
    void testDeserializability() throws Exception {
        DefaultFact.Header h = new ObjectMapper().readValue(
                "{\"id\":\"5d0e3ae9-6684-42bc-87a7-854f76506f7e\",\"ns\":\"ns\",\"type\":\"t\",\"meta\":{\"foo\":\"bar\"}}",
                DefaultFact.Header.class);
        assertEquals(UUID.fromString("5d0e3ae9-6684-42bc-87a7-854f76506f7e"), h.id());
        assertEquals("ns", h.ns());
        assertEquals("t", h.type());
        assertEquals("bar", h.meta().get("foo"));
    }

    @Test
    void testIgnoreExtra() throws Exception {
        DefaultFact.Header h = new ObjectMapper().readValue(
                "{\"id\":\"5d0e3ae9-6684-42bc-87a7-854f76506f7e\",\"ns\":\"ns\",\"type\":\"t\",\"bing\":\"bang\"}",
                DefaultFact.Header.class);
        assertEquals(UUID.fromString("5d0e3ae9-6684-42bc-87a7-854f76506f7e"), h.id());
        assertEquals("ns", h.ns());
        assertEquals("t", h.type());
    }
}
