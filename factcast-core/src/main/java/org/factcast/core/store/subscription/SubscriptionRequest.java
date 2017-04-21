package org.factcast.core.store.subscription;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.factcast.core.store.subscription.ClientSubscriptionRequest.SpecBuilder;

import com.google.common.base.Preconditions;

import lombok.NonNull;

public interface SubscriptionRequest {
	long maxLatencyInMillis();

	boolean continous();

	Optional<UUID> startingAfter();

	List<FactSpec> specs();

	public static SpecBuilder follow(@NonNull FactSpec spec) {
		return new ClientSubscriptionRequest.Builder(new ClientSubscriptionRequest()).follow(spec);
	}

	public static SpecBuilder follow(long maxLatencyInMillis, @NonNull FactSpec spec) {

		Preconditions.checkArgument(maxLatencyInMillis >= 10, "maxLatencyInMillis>=10");
		Preconditions.checkArgument(maxLatencyInMillis <= 300000, "maxLatencyInMillis<=300000");

		ClientSubscriptionRequest toBuild = new ClientSubscriptionRequest();
		toBuild.maxLatencyInMillis = maxLatencyInMillis;
		return new ClientSubscriptionRequest.Builder(toBuild).follow(spec);
	}

	public static SpecBuilder catchup(@NonNull FactSpec spec) {
		return new ClientSubscriptionRequest.Builder(new ClientSubscriptionRequest()).catchup(spec);
	}
}