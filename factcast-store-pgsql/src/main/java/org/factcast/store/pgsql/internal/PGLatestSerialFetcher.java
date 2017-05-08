package org.factcast.store.pgsql.internal;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

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
            return jdbcTemplate.queryForObject(PGConstants.SELECT_LATEST_SER, Long.class);
        } catch (EmptyResultDataAccessException meh) {
        }
        return 0;
    }

}
