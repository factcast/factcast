package org.factcast.store.pgsql.internal;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.factcast.core.Fact;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.ToString;
import lombok.experimental.Accessors;

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

	private final ObjectMapper jackson;
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
		Meta deser = jackson.readValue(jsonHeader, Meta.class);
		return deser.meta;
	}

	private static class Meta {
		Map<String, String> meta = new HashMap<>();
	}
}
