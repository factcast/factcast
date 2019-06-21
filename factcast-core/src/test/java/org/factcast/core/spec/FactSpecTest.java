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
package org.factcast.core.spec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.UUID;

import org.factcast.core.util.FactCastJson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.node.ObjectNode;

public class FactSpecTest {

    @Test
    void testMetaBothNull() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            FactSpec.ns("foo").meta(null, null);
        });
    }

    @Test
    void testMetaKeyNull() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            FactSpec.ns("foo").meta(null, "");
        });
    }

    @Test
    void testMetaValueNull() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            FactSpec.ns("foo").meta("", null);
        });
    }

    @Test
    void testFactSpecConstructorNull() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            new FactSpec(null);
        });
    }

    @SuppressWarnings("static-access")
    @Test
    void testFactSpecNs() {
        assertEquals("y", FactSpec.ns("x").ns("y").ns());
    }

    @Test
    void testFactSpecType() {
        assertEquals("y", FactSpec.ns("x").type("y").type());
    }

    @Test
    void testFactSpecAggId() {
        UUID id = UUID.randomUUID();
        assertEquals(id, FactSpec.ns("x").aggId(id).aggId());
    }

    @Test
    void testFactSpecJsFilter() {
        FactSpec ns = FactSpec.ns("x");
        ns = ns.jsFilterScript("foo");
        String script = ns.jsFilterScript();
        assertEquals("foo", script);
    }

    @Test
    void testFactSpecEquality() {
        FactSpec f1 = FactSpec.ns("x");
        FactSpec f2 = FactSpec.ns("x");
        assertEquals(f1, f2);
        assertNotSame(f1, f2);
    }

    @Test
    public void testJsFilterScriptDeserDownwardCompatibility() throws Exception {
        String script = "foo";
        String json = "{\"ns\":\"x\",\"jsFilterScript\":\"" + script + "\"}";

        FactSpec spec = FactCastJson.readValue(FactSpec.class, json);

        assertEquals(new FilterScript("js", script), spec.filterScript());
    }

    @Test
    public void testJsFilterScriptDeserRemoved() throws Exception {
        String script = "foo";
        String json = "{\"ns\":\"x\",\"jsFilterScript\":\"" + script + "\"}";

        FactSpec spec = FactCastJson.readValue(FactSpec.class, json);
        spec.filterScript(null);
        assertNull(spec.jsFilterScript());
        assertNull(spec.filterScript());
    }

    @Test
    public void testFilterScriptDeser() throws Exception {
        String script = "foo";
        String json = "{\"ns\":\"x\",\"filterScript\":{\"languageIdentifier\":\"js\",\"source\":\""
                + script + "\"}}";

        FactSpec spec = FactCastJson.readValue(FactSpec.class, json);
        assertEquals(script, spec.jsFilterScript());
        assertEquals(FilterScript.js(script), spec.filterScript());

        spec.filterScript(null);
        assertNull(spec.jsFilterScript());
        assertNull(spec.filterScript());
    }

    @Test
    public void testJsFilterScriptSerDownwardCompatibility() throws Exception {
        String expected = "foo";
        FactSpec fs = FactSpec.ns("x").filterScript(FilterScript.js("foo"));
        ObjectNode node = FactCastJson.toObjectNode(FactCastJson.writeValueAsString(fs));

        assertEquals(expected, node.get("jsFilterScript").asText());
    }

}
