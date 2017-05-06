package org.factcast.core.subscription;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.factcast.core.spec.FactSpec;
import org.factcast.core.subscription.FluentSubscriptionRequest.SpecBuilder;

import lombok.NonNull;

/**
 * Defines a request for Subscription.
 * 
 * see {@link FluentSubscriptionRequest}, {@link SubscriptionRequestTO}
 * 
 * @author uwe.schaefer@mercateo.com
 *
 */
public interface SubscriptionRequest {

    long maxBatchDelayInMs();

    boolean continous();

    boolean marks();

    boolean ephemeral();

    Optional<UUID> startingAfter();

    List<FactSpec> specs();

    String debugInfo();

    // ------------

    public static SpecBuilder follow(@NonNull FactSpec specification) {
        return new FluentSubscriptionRequest.Builder(new FluentSubscriptionRequest()).follow(
                specification);
    }

    public static SpecBuilder follow(long maxBatchDelayInMs, @NonNull FactSpec specification) {
        FluentSubscriptionRequest toBuild = new FluentSubscriptionRequest();
        toBuild.maxBatchDelayInMs = maxBatchDelayInMs;
        return new FluentSubscriptionRequest.Builder(toBuild).follow(specification);
    }

    public static SpecBuilder catchup(@NonNull FactSpec specification) {
        return new FluentSubscriptionRequest.Builder(new FluentSubscriptionRequest()).catchup(
                specification);
    }
}