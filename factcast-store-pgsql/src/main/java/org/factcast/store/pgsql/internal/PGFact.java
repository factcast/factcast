package org.factcast.store.pgsql.internal;

import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.factcast.core.Fact;
import org.factcast.core.util.FactCastJson;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.ToString;

/**
 * PG Specific implementation of a Fact.
 * 
 * This class is necessary in order to delay parsing of the header until
 * necessary (when accessing meta-data)
 * 
 * @author usr
 *
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@ToString(of = { "id", "ns", "type", "aggId", "meta" })
class PGFact implements Fact {

	@Getter
	@NonNull
	private final UUID id;
	@Getter
	@NonNull
	private final String ns;
	@Getter
	private final String type;
	@Getter
	private final UUID aggId;
	@Getter
	@NonNull
	private final String jsonHeader;
	@Getter
	@NonNull
	private final String jsonPayload;

	@JsonProperty
	private Map<String, String> meta = null;

	@Override
	public String meta(String key) {
		if (meta == null) {
			meta = deser();
		}
		return meta.get(key);
	}

	@SneakyThrows
	private Map<String, String> deser() {
		Meta deser = FactCastJson.reader().forType(Meta.class).readValue(jsonHeader);
		return deser.meta;
	}

	// just picks the MetaData from the Header (as we know the rest already
	private static class Meta {
		@JsonProperty
		final Map<String, String> meta = new HashMap<>();
	}

	@SneakyThrows
	public static Fact from(ResultSet rs) {

		String id = rs.getString(PGConstants.ALIAS_ID);
		String aggId = rs.getString(PGConstants.ALIAS_AGGID);
		String type = rs.getString(PGConstants.ALIAS_TYPE);
		String ns = rs.getString(PGConstants.ALIAS_NS);

		String jsonHeader = rs.getString(PGConstants.COLUMN_HEADER);
		String jsonPayload = rs.getString(PGConstants.COLUMN_PAYLOAD);

		return new PGFact(UUID.fromString(id), ns, type, aggId == null ? null : UUID.fromString(aggId), jsonHeader,
				jsonPayload);
	}
}
