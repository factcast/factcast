package org.factcast.store.pgsql.internal;

import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import java.sql.ResultSet;
import java.util.Set;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;

public class PGFactTest {

    @Before
    public void setUp() throws Exception {
    }

    @Test
    public void testFrom() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(true);
        String ns = "ns";
        String type = "type";
        String aggId = UUID.randomUUID().toString();
        String aggIdArr = "[\"" + aggId + "\"]";
        String id = aggId;
        String header = "{\"meta\":{\"foo\":\"1\",\"bar\":\"2\",\"baz\":\"3\"}}";
        String payload = "{}";

        when(rs.getString(eq(PGConstants.ALIAS_ID))).thenReturn(id);
        when(rs.getString(eq(PGConstants.ALIAS_NS))).thenReturn(ns);
        when(rs.getString(eq(PGConstants.ALIAS_TYPE))).thenReturn(type);
        when(rs.getString(eq(PGConstants.ALIAS_AGGID))).thenReturn(aggIdArr);

        when(rs.getString(eq(PGConstants.COLUMN_HEADER))).thenReturn(header);
        when(rs.getString(eq(PGConstants.COLUMN_PAYLOAD))).thenReturn(payload);
        when(rs.next()).thenReturn(true);

        PGFact uut = (PGFact) PGFact.from(rs);

        assertEquals(ns, uut.ns());
        assertEquals(type, uut.type());
        assertEquals(aggId, uut.aggId().iterator().next().toString());
        assertEquals(id, uut.id().toString());
        assertEquals(header, uut.jsonHeader());
        assertEquals(payload, uut.jsonPayload());

        assertEquals("1", uut.meta("foo"));
        assertEquals("2", uut.meta("bar"));
        assertEquals("3", uut.meta("baz"));

    }

    @Test
    public void testToUUIDArrayNull() throws Exception {
        Set<UUID> res = PGFact.toUUIDArray(null);
        assertTrue(res.isEmpty());
    }

    @Test
    public void testToUUIDArrayEmpty() throws Exception {
        Set<UUID> res = PGFact.toUUIDArray("[]");
        assertTrue(res.isEmpty());
    }

    @Test
    public void testToUUIDArraySingle() throws Exception {
        UUID aggId1 = UUID.randomUUID();
        Set<UUID> res = PGFact.toUUIDArray("[\"" + aggId1 + "\"]");
        assertEquals(1, res.size());
        assertTrue(res.contains(aggId1));
    }

    @Test
    public void testToUUIDArrayMutli() throws Exception {
        UUID aggId1 = UUID.randomUUID();
        UUID aggId2 = UUID.randomUUID();
        Set<UUID> res = PGFact.toUUIDArray("[\"" + aggId1 + "\",\"" + aggId2 + "\"]");
        assertEquals(2, res.size());
        assertTrue(res.contains(aggId1));
        assertTrue(res.contains(aggId2));
    }

}
