package org.factcast.core.store.subscription;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.factcast.core.wellknown.MarkFact;

import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(fluent = true)
@RequiredArgsConstructor(staticName = "ns")
@Data
/**
 * Defines a Specification of facts to match for a subscription. 
 * @author usr
 *
 */
public class FactSpec {
	@NonNull
	private final String ns;
	private String type = null;
	private UUID aggId = null;
	@NonNull
	private final Map<String, String> meta = new HashMap<>();

	public FactSpec meta(@NonNull String k, @NonNull String v) {
		meta.put(k, v);
		return this;
	}

	private String jsFilterScript = null;
	public static FactSpec forMark() {
		return FactSpec.ns("").type(MarkFact.TYPE);
	}
}
