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
package org.factcast.store.inmem;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

import org.factcast.core.Fact;
import org.junit.jupiter.api.Test;

public class InMemFactTest {

    @Test
    void testAddMeta() {
        Fact f1 = Fact.of("{\"ns\":\"someNs\",\"id\":\"" + UUID.randomUUID() + "\"}", "{}");
        InMemFact uut = new InMemFact(21, f1);
        assertEquals(21, uut.serial());
    }

    @Test
    void testAddToExistingMeta() {
        Fact f1 = Fact.of("{\"ns\":\"someNs\",\"id\":\"" + UUID.randomUUID()
                + "\", \"meta\":{\"foo\":\"bar\"}}", "{}");
        InMemFact uut = new InMemFact(12, f1);
        assertEquals(12, uut.serial());
        assertEquals("bar", uut.meta("foo"));
    }

    @Test
    void testReplaceFraudulentSer() {
        Fact f1 = Fact.of("{\"ns\":\"someNs\",\"id\":\"" + UUID.randomUUID()
                + "\", \"meta\":{\"_ser\":99999}}", "{}");
        assertEquals(99999, f1.serial());
        InMemFact uut = new InMemFact(12, f1);
        assertEquals(12, uut.serial());
    }
}
