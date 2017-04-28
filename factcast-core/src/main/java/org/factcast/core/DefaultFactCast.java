package org.factcast.core;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import org.factcast.core.store.FactStore;
import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.SubscriptionRequest;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.core.subscription.observer.FactObserver;
import org.factcast.core.subscription.observer.GenericObserver;
import org.factcast.core.subscription.observer.IdObserver;

import lombok.AccessLevel;
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
    private final FactStore store;

    @Override
    public Subscription subscribeToFacts(@NonNull SubscriptionRequest req,
            @NonNull FactObserver observer) {
        return store.subscribe(SubscriptionRequestTO.forFacts(req), observer);
    }

    @Override
    public Subscription subscribeToIds(@NonNull SubscriptionRequest req,
            @NonNull IdObserver observer) {
        Function<Fact, UUID> projection = f -> f.id();
        return store.subscribe(SubscriptionRequestTO.forIds(req), new ObserverBridge<UUID>(observer,
                projection));
    }

    @Override
    public Optional<Fact> fetchById(@NonNull UUID id) {
        return store.fetchById(id);
    }

    @Override
    public void publish(@NonNull List<? extends Fact> factsToPublish) {
        store.publish(factsToPublish);
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private static class ObserverBridge<T> implements FactObserver {

        private final GenericObserver<T> delegate;

        private final Function<Fact, T> project;

        @Override
        public void onNext(Fact fact) {
            delegate.onNext(project.apply(fact));
        }

        @Override
        public void onCatchup() {
            delegate.onCatchup();
        }

        @Override
        public void onError(Throwable exception) {
            delegate.onError(exception);
        }

        @Override
        public void onComplete() {
            delegate.onComplete();
        }
    }
}
