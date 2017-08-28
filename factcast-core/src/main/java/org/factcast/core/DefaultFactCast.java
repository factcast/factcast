package org.factcast.core;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

import org.factcast.core.store.FactStore;
import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.SubscriptionRequest;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.core.subscription.observer.FactObserver;
import org.factcast.core.subscription.observer.IdObserver;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Default impl for FactCast used by FactCast.from* methods.
 * 
 * @author uwe.schaefer@mercateo.com
 *
 */
@RequiredArgsConstructor
class DefaultFactCast implements FactCast {

    @NonNull
    final FactStore store;

    @Override
    @NonNull
    public Subscription subscribeToFacts(@NonNull SubscriptionRequest req,
            @NonNull FactObserver observer) {
        return store.subscribe(SubscriptionRequestTO.forFacts(req), observer);
    }

    @Override
    @NonNull
    public Subscription subscribeToIds(@NonNull SubscriptionRequest req,
            @NonNull IdObserver observer) {
        return store.subscribe(SubscriptionRequestTO.forIds(req), observer.map(Fact::id));
    }

    @Override
    @NonNull
    public Optional<Fact> fetchById(@NonNull UUID id) {
        return store.fetchById(id);
    }

    @Override
    public void publish(@NonNull List<? extends Fact> factsToPublish) {
        store.publish(factsToPublish);
    }

    @Override
    @NonNull
    public OptionalLong serialOf(@NonNull UUID id) {
        return store.serialOf(id);
    }

}
