package org.factcast.store.pgsql.internal;

import java.util.UUID;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import lombok.RequiredArgsConstructor;

/**
 * Fetches a SERIAL from a Fact-Id.
 * 
 * @author uwe.schaefer@mercateo.com
 *
 */
@RequiredArgsConstructor
class PGFactIdToSerMapper {
    final JdbcTemplate jdbcTemplate;

    final static String sql = "SELECT " + PGConstants.COLUMN_SER + " FROM " + PGConstants.TABLE_FACT
            + " WHERE " + PGConstants.COLUMN_HEADER + " @> ?";

    /**
     * 
     * @param id
     * @return 0, if no Fact is found for the id given. @throws
     */
    public long retrieve(UUID id) {
        if (id != null) {

            try {
                // throws EmptyResultDataAccessException if is not found!
                return jdbcTemplate.queryForObject(sql, new Object[] { "{\"id\":\"" + id + "\"}" },
                        Long.class).longValue();
            } catch (EmptyResultDataAccessException meh) {
            }
        }
        return 0;
    }

}
