package org.factcast.core;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;

@Accessors(fluent = true)
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class TestFact implements Fact {
	@JsonProperty
	UUID id = UUID.randomUUID();
	@JsonProperty
	UUID aggId;
	@JsonProperty
	String type;
	@JsonProperty
	String ns = "default";
	String jsonPayload;
	@JsonProperty
	Map<String, String> meta = new HashMap<>();

	@Override
	public String meta(String key) {
		return meta.get(key);
	}

	public TestFact meta(String key, String value) {
		meta.put(key, value);
		return this;
	}

	@Override
	@SneakyThrows
	public String jsonHeader() {
		return new ObjectMapper().writeValueAsString(this);
	}
}
