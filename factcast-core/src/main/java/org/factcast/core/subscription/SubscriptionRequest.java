package org.factcast.core.subscription;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.factcast.core.subscription.FluentSubscriptionRequest.SpecBuilder;

import com.google.common.base.Preconditions;

import lombok.NonNull;

public interface SubscriptionRequest {
	long maxBatchDelayInMs();

	boolean continous();

	boolean ephemeral();

	Optional<UUID> startingAfter();

	List<FactSpec> specs();

	// ------------

	public static SpecBuilder ephemeral(@NonNull FactSpec spec) {
		return new FluentSubscriptionRequest.Builder(new FluentSubscriptionRequest(true)).follow(spec);
	}

	public static SpecBuilder ephemeral(long maxBatchDelayInMs, @NonNull FactSpec spec) {

		checkMaxDelay(maxBatchDelayInMs);

		FluentSubscriptionRequest toBuild = new FluentSubscriptionRequest(true);
		toBuild.maxBatchDelayInMs = maxBatchDelayInMs;
		return new FluentSubscriptionRequest.Builder(toBuild).follow(spec);
	}

	public static SpecBuilder follow(@NonNull FactSpec spec) {
		return new FluentSubscriptionRequest.Builder(new FluentSubscriptionRequest(false)).follow(spec);
	}

	public static SpecBuilder follow(long maxBatchDelayInMs, @NonNull FactSpec spec) {

		checkMaxDelay(maxBatchDelayInMs);

		FluentSubscriptionRequest toBuild = new FluentSubscriptionRequest(false);
		toBuild.maxBatchDelayInMs = maxBatchDelayInMs;
		return new FluentSubscriptionRequest.Builder(toBuild).follow(spec);
	}

	public static void checkMaxDelay(long maxLatencyInMillis) {
		Preconditions.checkArgument(maxLatencyInMillis >= 10, "maxBatchDelayInMs>=10");
		Preconditions.checkArgument(maxLatencyInMillis <= 30000, "maxBatchDelayInMs<=30000");
	}

	public static SpecBuilder catchup(@NonNull FactSpec spec) {
		return new FluentSubscriptionRequest.Builder(new FluentSubscriptionRequest(false)).catchup(spec);
	}
}