package org.factcast.core.store.subscription;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.factcast.core.wellknown.MarkFact;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Defines a Specification of facts to match for a subscription.
 * 
 * @author usr
 *
 */
@Accessors(fluent = true)
@Data
@RequiredArgsConstructor(staticName = "ns")
public class FactSpec {

	@NonNull
	@JsonProperty
	private final String ns;
	@JsonProperty
	private String type = null;
	@JsonProperty
	private UUID aggId = null;
	@NonNull
	@JsonProperty
	private final Map<String, String> meta = new HashMap<>();

	public FactSpec meta(@NonNull String k, @NonNull String v) {
		meta.put(k, v);
		return this;
	}

	@JsonProperty
	private String jsFilterScript = null;

	public static FactSpec forMark() {
		return FactSpec.ns(MarkFact.NS).type(MarkFact.TYPE);
	}

	protected FactSpec() {
		this("default");
	}
}
