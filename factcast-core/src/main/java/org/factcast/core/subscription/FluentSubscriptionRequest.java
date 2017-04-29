package org.factcast.core.subscription;

import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import org.factcast.core.spec.FactSpec;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

/**
 * SubscriptionRequest intented to be used by clients for convenience.
 * 
 * @author uwe.schaefer@mercateo.com
 *
 */
@FieldDefaults(level = AccessLevel.PROTECTED)
@Getter
@ToString
@RequiredArgsConstructor
class FluentSubscriptionRequest implements SubscriptionRequest {

    boolean ephemeral = false;

    long maxBatchDelayInMs = 0;

    boolean continous;

    UUID startingAfter;

    List<FactSpec> specs = new LinkedList<>();

    boolean idOnly = false;

    @RequiredArgsConstructor
    public static class Builder implements SpecBuilder {
        private final FluentSubscriptionRequest toBuild;

        @Override
        public SpecBuilder or(@NonNull FactSpec specification) {
            toBuild.specs.add(specification);
            return this;
        }

        @Override
        public SubscriptionRequest sinceInception() {
            return toBuild;
        }

        @Override
        public SubscriptionRequest sinceSubscription() {
            toBuild.ephemeral = true;
            return toBuild;
        }

        @Override
        public SubscriptionRequest since(@NonNull UUID id) {
            toBuild.startingAfter = id;
            return toBuild;
        }

        SpecBuilder follow(@NonNull FactSpec specification) {
            or(specification);
            toBuild.continous = true;
            return this;
        }

        SpecBuilder catchup(@NonNull FactSpec specification) {
            or(specification);
            toBuild.continous = false;
            return this;
        }
    }

    public java.util.Optional<UUID> startingAfter() {
        return java.util.Optional.ofNullable(startingAfter);
    }

    public interface SpecBuilder {
        SpecBuilder or(@NonNull FactSpec specification);

        SubscriptionRequest since(@NonNull UUID id);

        SubscriptionRequest sinceInception();

        SubscriptionRequest sinceSubscription();

    }

}
