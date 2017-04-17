package org.factcast.store.pgsql.internal;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import org.factcast.core.Fact;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ResultSetExtractor;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class PGFactFactory implements ResultSetExtractor<Fact> {

	private final ObjectMapper om;

	@Override
	public Fact extractData(ResultSet rs) throws SQLException, DataAccessException {

		String id = rs.getString(PGConstants.ALIAS_ID);
		String aggId = rs.getString(PGConstants.ALIAS_AGGID);
		String type = rs.getString(PGConstants.ALIAS_TYPE);
		String ns = rs.getString(PGConstants.ALIAS_NS);

		String jsonHeader = rs.getString(PGConstants.COLUMN_HEADER);
		String jsonPayload = rs.getString(PGConstants.COLUMN_PAYLOAD);

		return new PGFact(id == null ? null : UUID.fromString(id), ns, type,
				aggId == null ? null : UUID.fromString(aggId), jsonHeader, jsonPayload, om);
	}

}
