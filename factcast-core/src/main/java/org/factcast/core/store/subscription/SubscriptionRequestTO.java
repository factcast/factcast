package org.factcast.core.store.subscription;

import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Data
@Accessors(fluent = true)
@NoArgsConstructor
public class SubscriptionRequestTO implements SubscriptionRequest {

	@JsonProperty
	long maxLatencyInMillis = 100;
	@JsonProperty
	boolean continous;
	@JsonProperty
	boolean idOnly = false;
	@JsonProperty
	UUID startingAfter;
	@JsonProperty
	List<FactSpec> specs = new LinkedList<>();

	public java.util.Optional<UUID> startingAfter() {
		return java.util.Optional.ofNullable(startingAfter);
	}

	// copy constr. from a SR
	public SubscriptionRequestTO(SubscriptionRequest request) {
		maxLatencyInMillis = request.maxLatencyInMillis();
		continous = request.continous();
		idOnly = request.idOnly();
		startingAfter = request.startingAfter().orElse(null);
		specs = request.specs();
	}

}
