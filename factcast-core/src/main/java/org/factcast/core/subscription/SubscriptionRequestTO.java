package org.factcast.core.subscription;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import org.factcast.core.util.FactCastJson;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Data
@NoArgsConstructor
@JsonIgnoreProperties
public class SubscriptionRequestTO implements SubscriptionRequest {

	@JsonProperty
	long maxBatchDelayInMs = 0;
	@JsonProperty
	boolean continous;
	@JsonProperty
	boolean ephemeral;
	@JsonProperty
	boolean idOnly = false;
	@JsonProperty
	UUID startingAfter;
	@JsonProperty
	List<FactSpec> specs = new LinkedList<>();

	transient Boolean hasScriptFilters;

	public boolean hasAnyScriptFilters() {
		if (hasScriptFilters == null) {
			hasScriptFilters = specs.stream().anyMatch(s -> s.jsFilterScript() != null);
		}
		return hasScriptFilters;
	}

	public java.util.Optional<UUID> startingAfter() {
		return java.util.Optional.ofNullable(startingAfter);
	}

	// copy constr. from a SR
	public SubscriptionRequestTO(SubscriptionRequest request) {
		maxBatchDelayInMs = request.maxBatchDelayInMs();
		continous = request.continous();
		ephemeral = request.ephemeral();
		startingAfter = request.startingAfter().orElse(null);
		specs = new ArrayList<FactSpec>(request.specs());
		specs.add(0, FactSpec.forMark());
		specs = Collections.unmodifiableList(specs);
	}

	public static SubscriptionRequestTO forFacts(SubscriptionRequest req) {
		SubscriptionRequestTO t = new SubscriptionRequestTO(req);
		t.idOnly(false);
		return t;
	}

	public static SubscriptionRequestTO forIds(SubscriptionRequest req) {
		SubscriptionRequestTO t = new SubscriptionRequestTO(req);
		t.idOnly(true);
		return t;
	}

	public SubscriptionRequestTO copy() {
		return FactCastJson.copy(this);
	}
}
