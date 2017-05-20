package org.factcast.store.pgsql.internal;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.factcast.core.Fact;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.RowMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class PGCatchupFetchPage {
    final JdbcTemplate jdbc;

    final long clientId;

    final long pageSize;

    public List<Fact> fetchFacts(AtomicLong serial) {
        final RowMapper<Fact> uuidExtractor = (rs, i) -> {
            serial.set(rs.getLong(PGConstants.COLUMN_SER));
            return PGFact.from(rs);
        };

        final PreparedStatementSetter pss = ps -> {
            ps.setLong(1, clientId);
            ps.setLong(2, serial.get());
            ps.setLong(3, pageSize);
        };

        log.debug("  fetching next page for cid={}, limit={}, ser>{}", clientId, pageSize, serial
                .get());
        return jdbc.query(PGConstants.SELECT_FACT_FROM_CATCHUP, pss, uuidExtractor);
    }

    public List<UUID> fetchIDs(AtomicLong serial) {
        final RowMapper<UUID> uuidExtractor = (rs, i) -> {
            serial.set(rs.getLong(PGConstants.COLUMN_SER));
            return UUID.fromString(rs.getString(PGConstants.ALIAS_ID));
        };
        final PreparedStatementSetter pss = ps -> {
            ps.setLong(1, clientId);
            ps.setLong(2, serial.get());
            ps.setLong(3, pageSize);
        };

        log.debug("  fetching for cid={} next {} ids", clientId, pageSize);
        return jdbc.query(PGConstants.SELECT_ID_FROM_CATCHUP, pss, uuidExtractor);
    }
}
