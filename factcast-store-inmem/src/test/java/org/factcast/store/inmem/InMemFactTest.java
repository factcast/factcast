/*
 * Copyright © 2018 factcast (http://factcast.org)
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
import static org.junit.jupiter.api.Assertions.assertNotSame;

import java.util.UUID;

import org.factcast.core.DefaultFact;
import org.factcast.core.Fact;
import org.factcast.core.util.FactCastJson;
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

    @Test
    public void testToString() throws Exception {
        Fact f = Fact.of("{\"ns\":\"someNs\",\"id\":\"" + UUID.randomUUID()
                + "\"}", "{}");

        InMemFact f1 = new InMemFact(32, f);
        Fact d = FactCastJson.readValue(DefaultFact.class, f1.toString());
        // needs to be constructed via of, in order to pass _ser handling
        Fact fact = Fact.of(d.jsonHeader(), d.jsonPayload());

        InMemFact f2 = new InMemFact(fact.serial(), fact);

        assertNotSame(f1, f2);
        assertEquals(f1, f2);

    }
}
