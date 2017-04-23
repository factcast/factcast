package org.factcast.core.store;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.factcast.core.Fact;
import org.factcast.core.subscription.FactStoreObserver;
import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.SubscriptionRequestTO;

import lombok.NonNull;

/**
 * A read/Write FactStore.
 * 
 * @author usr
 *
 */
public interface FactStore {

	public void publish(@NonNull List<? extends Fact> factsToPublish);

	CompletableFuture<Subscription> subscribe(@NonNull SubscriptionRequestTO req, @NonNull FactStoreObserver observer);

	Optional<Fact> fetchById(@NonNull UUID id);

}
