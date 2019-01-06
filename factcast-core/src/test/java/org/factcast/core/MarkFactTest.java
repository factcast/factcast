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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class MarkFactTest {

    final MarkFact uut = new MarkFact();

    @Test
    void testJsonPayload() {
        assertEquals("{}", new MarkFact().jsonPayload());
    }

    @Test
    void testJsonHeader() {
        assertNotNull(uut.jsonHeader());
        // intentionally not using the constants here. i am sure you see why :)
        assertEquals("_", uut.ns());
        assertEquals("_mark", uut.type());
        assertTrue(uut.aggIds().isEmpty());
        assertNotNull(uut.id());
    }

    @Test
    void testMeta() {
        assertNull(new MarkFact().meta("any"));
    }
}
