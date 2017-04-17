package org.factcast.store.pgsql.internal;

import java.util.UUID;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
class PGFactIdToSerMapper {
	private final JdbcTemplate tpl;

	public long retrieve(UUID afterId) throws EmptyResultDataAccessException {
		if (afterId != null) {
			// throws exception if is not found!
			return tpl.queryForObject(
					"SELECT " + PGConstants.COLUMN_SER + " FROM " + PGConstants.TABLE_FACT + " WHERE "
							+ PGConstants.COLUMN_HEADER + " @> ?",
					new Object[] { "{\"id\":\"" + afterId + "\"}" }, Long.class).longValue();

		}
		return 0;
	}

}
