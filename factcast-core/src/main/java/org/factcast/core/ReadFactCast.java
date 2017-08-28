package org.factcast.core;

import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.SubscriptionRequest;
import org.factcast.core.subscription.observer.FactObserver;
import org.factcast.core.subscription.observer.IdObserver;

import lombok.NonNull;

/**
 * A read-only interface to a FactCast, that only offers subscription and
 * Fact-by-id lookup.
 * 
 * @author uwe.schaefer@mercateo.com
 *
 */
public interface ReadFactCast {
    Subscription subscribeToFacts(@NonNull SubscriptionRequest request,
            @NonNull FactObserver observer);

    Subscription subscribeToIds(@NonNull SubscriptionRequest request, @NonNull IdObserver observer);

    Optional<Fact> fetchById(@NonNull UUID id);

    @NonNull
    OptionalLong serialOf(@NonNull UUID id);
}
