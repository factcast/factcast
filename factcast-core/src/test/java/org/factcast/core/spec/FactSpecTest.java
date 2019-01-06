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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.factcast.core.MarkFact;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

// TODO remove?
public class FactSpecTest {

    @Test
    void testMarkMatcher() {
        assertTrue(new FactSpecMatcher(FactSpec.forMark()).test(new MarkFact()));
    }

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
        assertEquals("foo", FactSpec.ns("x").jsFilterScript("foo").jsFilterScript());
    }

    @Test
    void testFactSpecEquality() {
        FactSpec f1 = FactSpec.ns("x");
        FactSpec f2 = FactSpec.ns("x");
        assertEquals(f1, f2);
        assertNotSame(f1, f2);
    }
}
