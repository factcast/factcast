package org.factcast.core.subscription;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.factcast.core.subscription.FluentSubscriptionRequest.SpecBuilder;

import com.google.common.base.Preconditions;

import lombok.NonNull;

public interface SubscriptionRequest {
	long maxLatencyInMillis();

	boolean continous();

	Optional<UUID> startingAfter();

	List<FactSpec> specs();

	boolean idOnly();

	// hint to where to get the default from
	public static SpecBuilder follow(@NonNull FactSpec spec) {
		return new FluentSubscriptionRequest.Builder(new FluentSubscriptionRequest()).follow(spec);
	}

	// hint to where to get the default from
	public static SpecBuilder follow(long maxLatencyInMillis, @NonNull FactSpec spec) {

		Preconditions.checkArgument(maxLatencyInMillis >= 10, "maxLatencyInMillis>=10");
		Preconditions.checkArgument(maxLatencyInMillis <= 300000, "maxLatencyInMillis<=300000");

		FluentSubscriptionRequest toBuild = new FluentSubscriptionRequest();
		toBuild.maxLatencyInMillis = maxLatencyInMillis;
		return new FluentSubscriptionRequest.Builder(toBuild).follow(spec);
	}

	// hint to where to get the default from
	public static SpecBuilder catchup(@NonNull FactSpec spec) {
		return new FluentSubscriptionRequest.Builder(new FluentSubscriptionRequest()).catchup(spec);
	}
}