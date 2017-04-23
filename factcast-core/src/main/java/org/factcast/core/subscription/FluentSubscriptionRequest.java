package org.factcast.core.subscription;

import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

/**
 * SubscriptionRequest intented to be used by clients for convenience.
 * 
 * @author usr
 *
 */
@FieldDefaults(level = AccessLevel.PROTECTED)
@Getter

@ToString
@RequiredArgsConstructor
class FluentSubscriptionRequest implements SubscriptionRequest {

	final boolean ephemeral;

	long maxBatchDelayInMs = 0;
	boolean continous;
	UUID startingAfter;
	List<FactSpec> specs = new LinkedList<>();
	boolean idOnly = false;

	@RequiredArgsConstructor
	public static class Builder implements SpecBuilder {
		private final FluentSubscriptionRequest toBuild;

		@Override
		public SpecBuilder or(@NonNull FactSpec s) {
			toBuild.specs.add(s);
			return this;
		}

		@Override
		public SubscriptionRequest sinceInception() {
			return toBuild;
		}

		@Override
		public SubscriptionRequest since(@NonNull UUID id) {
			toBuild.startingAfter = id;
			return toBuild;
		}

		SpecBuilder follow(@NonNull FactSpec spec) {
			or(spec);
			toBuild.continous = true;
			return this;
		}

		SpecBuilder catchup(@NonNull FactSpec spec) {
			or(spec);
			toBuild.continous = false;
			return this;
		}

	}

	public java.util.Optional<UUID> startingAfter() {
		return java.util.Optional.ofNullable(startingAfter);
	}

	public interface SpecBuilder {
		SpecBuilder or(@NonNull FactSpec s);

		SubscriptionRequest since(@NonNull UUID id);

		SubscriptionRequest sinceInception();

	}

}
