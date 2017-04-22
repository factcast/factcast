package org.factcast.store.pgsql.internal;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.factcast.core.Fact;
import org.factcast.core.util.FCJson;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * PG Specific impl of Fact.
 * 
 * This class is necessary in order to delay parsing of the header until
 * necessary (when accessing meta-data)
 * 
 * @author usr
 *
 */
@Accessors(fluent = true)
@RequiredArgsConstructor
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
	Map<String, String> meta = null;

	@Override
	public String meta(String key) {
		if (meta == null) {
			meta = deser();
		}
		return meta.get(key);
	}

	@SneakyThrows
	private Map<String, String> deser() {
		Meta deser = FCJson.reader().forType(Meta.class).readValue(jsonHeader);
		return deser.meta;
	}

	// just picks the MetaData from the Header (as we know the rest already
	private static class Meta {
		@JsonProperty
		Map<String, String> meta = new HashMap<>();
	}
}
