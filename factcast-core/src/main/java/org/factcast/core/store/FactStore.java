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
 * Where FactCast is an interface to work with as an application, FactStore is
 * something that FactCast impls use to actually store and retrieve Facts.
 * 
 * In a sense it is an internal interface, or SPI implemented by for instance
 * InMemFactStore or PGFactStore.
 * 
 * @author usr
 *
 */
public interface FactStore {

    public void publish(@NonNull List<? extends Fact> factsToPublish);

    CompletableFuture<Subscription> subscribe(@NonNull SubscriptionRequestTO request,
            @NonNull FactStoreObserver observer);

    Optional<Fact> fetchById(@NonNull UUID id);

}
