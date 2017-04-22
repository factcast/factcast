package org.factcast.core;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.factcast.core.subscription.FactObserver;
import org.factcast.core.subscription.IdObserver;
import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.SubscriptionRequest;

import lombok.NonNull;

public interface ReadFactCast {
	CompletableFuture<Subscription> subscribe(@NonNull SubscriptionRequest req, @NonNull FactObserver observer);

	CompletableFuture<Subscription> subscribe(@NonNull SubscriptionRequest req, @NonNull IdObserver observer);

	Optional<Fact> fetchById(@NonNull UUID id);
}
