package org.factcast.core;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.factcast.core.subscription.FactObserver;
import org.factcast.core.subscription.IdObserver;
import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.SubscriptionRequest;

import lombok.NonNull;

/**
 * A read-only interface to a FactCast, thah only offers subscription and
 * Fact-by-id lookup.
 * 
 * @author usr
 *
 */
public interface ReadFactCast {
	CompletableFuture<Subscription> subscribeToFacts(@NonNull SubscriptionRequest req, @NonNull FactObserver observer);

	CompletableFuture<Subscription> subscribeToIds(@NonNull SubscriptionRequest req, @NonNull IdObserver observer);

	Optional<Fact> fetchById(@NonNull UUID id);
}
