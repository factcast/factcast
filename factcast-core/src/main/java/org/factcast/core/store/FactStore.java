package org.factcast.core.store;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.factcast.core.Fact;
import org.factcast.core.store.subscription.FactStoreObserver;
import org.factcast.core.store.subscription.Subscription;
import org.factcast.core.store.subscription.SubscriptionRequest;

import lombok.NonNull;

/**
 * A read/Write FactStore.
 * 
 * @author usr
 *
 */
public interface FactStore {

	void publish(@NonNull List<Fact> factsToPublish);

	CompletableFuture<Subscription> subscribe(@NonNull SubscriptionRequest req, @NonNull FactStoreObserver observer);

	Optional<Fact> fetchById(@NonNull UUID id);

}
