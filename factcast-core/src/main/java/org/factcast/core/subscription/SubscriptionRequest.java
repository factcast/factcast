package org.factcast.core.subscription;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.factcast.core.spec.FactSpec;
import org.factcast.core.subscription.FluentSubscriptionRequest.SpecBuilder;

import com.google.common.base.Preconditions;

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

    boolean ephemeral();

    Optional<UUID> startingAfter();

    List<FactSpec> specs();

    // ------------

    public static SpecBuilder follow(@NonNull FactSpec specification) {
        return new FluentSubscriptionRequest.Builder(new FluentSubscriptionRequest()).follow(
                specification);
    }

    public static SpecBuilder follow(long maxBatchDelayInMs, @NonNull FactSpec specification) {

        checkMaxDelay(maxBatchDelayInMs);

        FluentSubscriptionRequest toBuild = new FluentSubscriptionRequest();
        toBuild.maxBatchDelayInMs = maxBatchDelayInMs;
        return new FluentSubscriptionRequest.Builder(toBuild).follow(specification);
    }

    public static void checkMaxDelay(long maxLatencyInMillis) {
        Preconditions.checkArgument(maxLatencyInMillis <= 30000, "maxBatchDelayInMs<=30000");
    }

    public static SpecBuilder catchup(@NonNull FactSpec specification) {
        return new FluentSubscriptionRequest.Builder(new FluentSubscriptionRequest()).catchup(
                specification);
    }
}