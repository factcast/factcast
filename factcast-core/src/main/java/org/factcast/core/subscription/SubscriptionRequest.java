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

	boolean ephemeral();

	Optional<UUID> startingAfter();

	List<FactSpec> specs();

	// ------------

	public static SpecBuilder ephemeral(@NonNull FactSpec spec) {
		return new FluentSubscriptionRequest.Builder(new FluentSubscriptionRequest(true)).follow(spec);
	}

	public static SpecBuilder ephemeral(long maxLatencyInMillis, @NonNull FactSpec spec) {

		checkMaxDelay(maxLatencyInMillis);

		FluentSubscriptionRequest toBuild = new FluentSubscriptionRequest(true);
		toBuild.maxLatencyInMillis = maxLatencyInMillis;
		return new FluentSubscriptionRequest.Builder(toBuild).follow(spec);
	}

	public static SpecBuilder follow(@NonNull FactSpec spec) {
		return new FluentSubscriptionRequest.Builder(new FluentSubscriptionRequest(false)).follow(spec);
	}

	public static SpecBuilder follow(long maxLatencyInMillis, @NonNull FactSpec spec) {

		checkMaxDelay(maxLatencyInMillis);

		FluentSubscriptionRequest toBuild = new FluentSubscriptionRequest(false);
		toBuild.maxLatencyInMillis = maxLatencyInMillis;
		return new FluentSubscriptionRequest.Builder(toBuild).follow(spec);
	}

	public static void checkMaxDelay(long maxLatencyInMillis) {
		Preconditions.checkArgument(maxLatencyInMillis >= 10, "maxLatencyInMillis>=10");
		Preconditions.checkArgument(maxLatencyInMillis <= 300000, "maxLatencyInMillis<=300000");
	}

	public static SpecBuilder catchup(@NonNull FactSpec spec) {
		return new FluentSubscriptionRequest.Builder(new FluentSubscriptionRequest(false)).catchup(spec);
	}
}