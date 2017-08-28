package org.factcast.core.store;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

import org.factcast.core.Fact;
import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.core.subscription.observer.FactObserver;

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
 * @author uwe.schaefer@mercateo.com
 *
 */
public interface FactStore {

    void publish(@NonNull List<? extends Fact> factsToPublish);

    Subscription subscribe(@NonNull SubscriptionRequestTO request, @NonNull FactObserver observer);

    Optional<Fact> fetchById(@NonNull UUID id);

    OptionalLong serialOf(@NonNull UUID l);

}
