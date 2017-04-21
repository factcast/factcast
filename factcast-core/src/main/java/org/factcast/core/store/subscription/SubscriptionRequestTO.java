package org.factcast.core.store.subscription;

import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Data
@Accessors(fluent = true)
public class SubscriptionRequestTO implements SubscriptionRequest {

	long maxLatencyInMillis = 100;
	boolean continous;
	UUID startingAfter;
	List<FactSpec> specs = new LinkedList<>();

	public java.util.Optional<UUID> startingAfter() {
		return java.util.Optional.ofNullable(startingAfter);
	}

}
