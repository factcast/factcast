package org.factcast.server.grpc.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.factcast.core.Fact;
import org.factcast.core.FactObserver;
import org.factcast.core.IdObserver;
import org.factcast.core.store.subscription.Subscription;
import org.factcast.core.store.subscription.SubscriptionRequest;

import lombok.NonNull;

public interface RemoteFactStore {
	CompletableFuture<Subscription> subscribe(@NonNull SubscriptionRequest req, @NonNull FactObserver observer);

	CompletableFuture<Subscription> subscribe(@NonNull SubscriptionRequest req, @NonNull IdObserver observer);

	Optional<Fact> fetchById(@NonNull UUID id);

	void publish(@NonNull List<Fact> factsToPublish);

}
