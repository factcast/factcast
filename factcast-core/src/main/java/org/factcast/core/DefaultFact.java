package org.factcast.core;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.factcast.core.util.FCJson;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Getter;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.Accessors;

/**
 * note: creating an instance involves deserializing the header from JS. This is probably not optimal considering performance. If you extend FactCast, consider creating a dedicated Fact Impl.
 * 
 * @see PGFact
 * @author usr
 *
 */
@Accessors(fluent = true)
@Getter
@ToString
class DefaultFact implements Fact {

	@NonNull
	private final String jsonHeader;
	@NonNull
	private final String jsonPayload;
	private Header header;
	private Map<String, String> meta;
	private String ns;
	private UUID aggId;
	private UUID id;
	private String type;

	public static Fact of(@NonNull String jsonHeader, @NonNull String jsonPayload){
		return new DefaultFact(jsonHeader, jsonPayload);
	}
	
	@SneakyThrows
	public DefaultFact(@NonNull String jsonHeader, @NonNull String jsonPayload) {
		header = FCJson.reader().forType(Header.class).readValue(jsonHeader);
		id = header.id;
		aggId = header.aggId;
		ns = header.ns;
		type = header.type;
		meta = header.meta;
		this.jsonHeader = jsonHeader;
		this.jsonPayload = jsonPayload;
	}

	@Value
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Header {
		@JsonProperty
		@NonNull
		final UUID id;
		@JsonProperty
		@NonNull
		final String ns;
		@JsonProperty
		final String type;
		@JsonProperty
		final UUID aggId;
		@JsonProperty
		final Map<String, String> meta=new HashMap<>();
	}

	@Override
	public String meta(String key) {
		return meta.get(key);
	}

}
