package org.factcast.core;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.factcast.core.util.FactCastJson;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;

import lombok.Getter;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.ToString;
import lombok.Value;

/**
 * Note: creating an instance involves deserializing the header from JS. This is
 * probably not optimal considering performance. If you extend FactCast,
 * consider creating a dedicated Fact Impl.
 * 
 * For caching purposes, this thing should be Externalizable.
 * 
 * @see PGFact
 * @author usr
 *
 */
@ToString
public class DefaultFact implements Fact, Externalizable {

	@Getter
	private String jsonHeader;
	@Getter
	private String jsonPayload;
	private Header header;

	// needed for Externalizable – do not use !
	@Deprecated
	public DefaultFact() {
	}

	public static Fact of(@NonNull String jsonHeader, @NonNull String jsonPayload) {
		return new DefaultFact(jsonHeader, jsonPayload);
	}

	@SneakyThrows
	private DefaultFact(@NonNull String jsonHeader, @NonNull String jsonPayload) {

		this.jsonHeader = jsonHeader;
		this.jsonPayload = jsonPayload;
		init(jsonHeader);

	}

	private void init(@NonNull String jsonHeader) throws IOException, JsonProcessingException {
		header = FactCastJson.reader().forType(Header.class).readValue(jsonHeader);
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
		final Map<String, String> meta = new HashMap<>();
	}

	@Override
	public String meta(String key) {
		return header.meta.get(key);
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		// write only header & payload
		out.writeUTF(jsonHeader);
		out.writeUTF(jsonPayload);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		// read only header & payload
		jsonHeader = in.readUTF();
		jsonPayload = in.readUTF();
		// and recreate the header field
		init(jsonHeader);
	}

	@Override
	public UUID id() {
		return header.id;
	}

	@Override
	public String ns() {
		return header.ns;
	}

	@Override
	public String type() {
		return header.type;
	}

	@Override
	public UUID aggId() {
		return header.aggId;
	}

}
