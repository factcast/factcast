package org.factcast.core.store;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.factcast.core.Fact;
import org.factcast.core.store.subscription.FactStoreObserver;
import org.factcast.core.store.subscription.Subscription;
import org.factcast.core.store.subscription.SubscriptionRequest;

import lombok.NonNull;

public interface ReadFactStore {

	CompletableFuture<Subscription> subscribe(@NonNull SubscriptionRequest req, @NonNull FactStoreObserver observer);

	Optional<Fact> fetchById(@NonNull UUID id);
}
