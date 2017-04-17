package org.factcast.core.store.subscription;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PROTECTED)
@Getter
@Accessors(fluent = true)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@ToString
public class SubscriptionRequest {

	long maxLatencyInMillis = 100;
	boolean continous;
	UUID startingAfter;
	List<FactSpec> specs = new LinkedList<>(Arrays.asList(FactSpec.forMark()));

	@RequiredArgsConstructor
	public static class Builder implements SpecBuilder {
		private final SubscriptionRequest toBuild;

		@Override
		public SpecBuilder or(@NonNull FactSpec s) {
			toBuild.specs.add(s);
			return this;
		}

		@Override
		public SubscriptionRequest sinceInception() {
			toBuild.lock();
			return toBuild;
		}

		@Override
		public SubscriptionRequest since(@NonNull UUID id) {
			toBuild.startingAfter = id;
			toBuild.lock();
			return toBuild;
		}

		private SpecBuilder follow(@NonNull FactSpec spec) {
			or(spec);
			toBuild.continous = true;
			return this;
		}

		private SpecBuilder catchup(@NonNull FactSpec spec) {
			or(spec);
			toBuild.continous = false;
			return this;
		}
	}

	public java.util.Optional<UUID> startingAfter() {
		return java.util.Optional.ofNullable(startingAfter);
	}

	protected void lock() {
		specs = ImmutableList.copyOf(specs);
	}

	public interface SpecBuilder {
		SpecBuilder or(@NonNull FactSpec s);

		SubscriptionRequest since(@NonNull UUID id);

		SubscriptionRequest sinceInception();

	}

	public static SpecBuilder follow(@NonNull FactSpec spec) {
		return new SubscriptionRequest.Builder(new SubscriptionRequest()).follow(spec);
	}

	public static SpecBuilder follow(long maxLatencyInMillis, @NonNull FactSpec spec) {

		Preconditions.checkArgument(maxLatencyInMillis >= 10, "maxLatencyInMillis>=10");
		Preconditions.checkArgument(maxLatencyInMillis <= 300000, "maxLatencyInMillis<=300000");

		SubscriptionRequest toBuild = new SubscriptionRequest();
		toBuild.maxLatencyInMillis = maxLatencyInMillis;
		return new SubscriptionRequest.Builder(toBuild).follow(spec);
	}

	public static SpecBuilder catchup(@NonNull FactSpec spec) {
		return new SubscriptionRequest.Builder(new SubscriptionRequest()).catchup(spec);
	}

}
