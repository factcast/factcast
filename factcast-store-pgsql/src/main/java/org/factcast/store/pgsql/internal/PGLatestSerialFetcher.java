package org.factcast.store.pgsql.internal;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.rowset.SqlRowSet;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Fetches the latest SERIAL from the fact table.
 * 
 * @author uwe.schaefer@mercateo.com
 *
 */
@RequiredArgsConstructor
class PGLatestSerialFetcher {
    @NonNull
    final JdbcTemplate jdbcTemplate;

    /**
     * 
     * @param id
     * @return 0, if no Fact is found
     */
    public long retrieveLatestSer() {

        try {
            SqlRowSet rs = jdbcTemplate.queryForRowSet(PGConstants.SELECT_LATEST_SER);
            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (EmptyResultDataAccessException meh) {
        }
        return 0;
    }

}
