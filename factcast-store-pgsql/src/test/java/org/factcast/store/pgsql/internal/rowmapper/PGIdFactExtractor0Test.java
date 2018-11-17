package org.factcast.store.pgsql.internal.rowmapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.factcast.core.Fact;
import org.factcast.store.pgsql.internal.PGConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class PGIdFactExtractor0Test {

    private AtomicLong serial = new AtomicLong(5);

    private PGIdFactExtractor uut = new PGIdFactExtractor(serial);

    @Test
    public void testMapRow() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        final UUID id = UUID.randomUUID();
        when(rs.getString(PGConstants.ALIAS_ID)).thenReturn(id.toString());
        when(rs.getLong(PGConstants.COLUMN_SER)).thenReturn(27L);
        Fact mapRow = uut.mapRow(rs, 1);
        assertEquals(27, serial.get());
        assertEquals(id, mapRow.id());
    }
}
