/*
 * Copyright © 2017-2020 factcast.org
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
package org.factcast.store.pgsql.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

public class PgFactTest {

    @Test
    void testFrom() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(true);
        String ns = "ns";
        String type = "type";
        String aggId = UUID.randomUUID().toString();
        String aggIdArr = "[\"" + aggId + "\"]";
        String header = "{\"meta\":{\"foo\":\"1\",\"bar\":\"2\",\"baz\":\"3\"}}";
        String payload = "{}";
        when(rs.getString(eq(PgConstants.ALIAS_ID))).thenReturn(aggId);
        when(rs.getString(eq(PgConstants.ALIAS_NS))).thenReturn(ns);
        when(rs.getString(eq(PgConstants.ALIAS_TYPE))).thenReturn(type);
        when(rs.getString(eq(PgConstants.ALIAS_AGGID))).thenReturn(aggIdArr);
        when(rs.getString(eq(PgConstants.COLUMN_HEADER))).thenReturn(header);
        when(rs.getString(eq(PgConstants.COLUMN_PAYLOAD))).thenReturn(payload);
        when(rs.next()).thenReturn(true);
        PgFact uut = (PgFact) PgFact.from(rs);
        assertEquals(ns, uut.ns());
        assertEquals(type, uut.type());
        assertEquals(aggId, uut.aggIds().iterator().next().toString());
        assertEquals(aggId, uut.id().toString());
        assertEquals(header, uut.jsonHeader());
        assertEquals(payload, uut.jsonPayload());
        assertEquals("1", uut.meta("foo"));
        assertEquals("2", uut.meta("bar"));
        assertEquals("3", uut.meta("baz"));
    }

    @Test
    void testToUUIDArrayNull() {
        Set<UUID> res = PgFact.toUUIDArray(null);
        assertTrue(res.isEmpty());
    }

    @Test
    void testToUUIDArrayEmpty() {
        Set<UUID> res = PgFact.toUUIDArray("[]");
        assertTrue(res.isEmpty());
    }

    @Test
    void testToUUIDArraySingle() {
        UUID aggId1 = UUID.randomUUID();
        Set<UUID> res = PgFact.toUUIDArray("[\"" + aggId1 + "\"]");
        assertEquals(1, res.size());
        assertTrue(res.contains(aggId1));
    }

    @Test
    void testToUUIDArrayMutli() {
        UUID aggId1 = UUID.randomUUID();
        UUID aggId2 = UUID.randomUUID();
        Set<UUID> res = PgFact.toUUIDArray("[\"" + aggId1 + "\",\"" + aggId2 + "\"]");
        assertEquals(2, res.size());
        assertTrue(res.contains(aggId1));
        assertTrue(res.contains(aggId2));
    }
}
