package org.factcast.store.pgsql.internal.rowmapper;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.sql.ResultSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.factcast.core.Fact;
import org.factcast.store.pgsql.internal.PGConstants;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class PGIdFactExtractor0Test {

    private AtomicLong serial = new AtomicLong(5);

    private PGIdFactExtractor uut = new PGIdFactExtractor(serial);

    @Before
    public void setUp() throws Exception {
    }

    @Test
    public void testMapRow() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(true, false);
        final UUID id = UUID.randomUUID();
        when(rs.getString(PGConstants.ALIAS_ID)).thenReturn(id.toString());
        when(rs.getLong(PGConstants.COLUMN_SER)).thenReturn(27L);

        Fact mapRow = uut.mapRow(rs, 1);

        assertEquals(27, serial.get());
        assertEquals(id, mapRow.id());
    }

}
