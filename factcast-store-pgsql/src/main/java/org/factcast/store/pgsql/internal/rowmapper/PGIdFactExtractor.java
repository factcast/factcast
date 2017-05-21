package org.factcast.store.pgsql.internal.rowmapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.factcast.core.Fact;
import org.factcast.core.IdOnlyFact;
import org.factcast.store.pgsql.internal.PGConstants;
import org.springframework.jdbc.core.RowMapper;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class PGIdFactExtractor implements RowMapper<Fact> {

    final AtomicLong serial;

    @Override
    public Fact mapRow(ResultSet rs, int rowNum) throws SQLException {
        serial.set(rs.getLong(PGConstants.COLUMN_SER));
        return new IdOnlyFact(UUID.fromString(rs.getString(PGConstants.ALIAS_ID)));

    }

}
