package org.factcast.client.cache;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

import org.factcast.core.Fact;
import org.factcast.core.FactCast;
import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.SubscriptionRequest;
import org.factcast.core.subscription.observer.FactObserver;
import org.factcast.core.subscription.observer.IdObserver;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Integrates local cache for Facts.
 * 
 * Note that this Wrapper does not necessarily make your consumers faster, as
 * the local caching might involve IO. Local caching is a good strategy where,
 * the same Fact is read very often by either restarting Consumers using
 * non-snapshot transient state, or where there are many local consumers that
 * share interest in the same Facts.
 * 
 * @author uwe.schaefer@mercateo.com
 *
 */

@Slf4j
@RequiredArgsConstructor
public class CachingFactCast implements FactCast {

    final FactCast delegate;

    final CachingFactLookup lookup;

    @Override
    public void publish(@NonNull List<? extends Fact> factsToPublish) {
        delegate.publish(factsToPublish);
    }

    @Override
    public Subscription subscribeToIds(SubscriptionRequest req, IdObserver observer) {
        return delegate.subscribeToIds(req, observer);
    }

    @Override
    public Subscription subscribeToFacts(SubscriptionRequest req, FactObserver observer) {

        log.debug("changing Fact Subscription to Id subscription for caching single Fact lookups");

        return subscribeToIds(req, new IdObserver() {

            @Override
            public void onNext(UUID f) {
                fetchById(f).ifPresent(observer::onNext);
            }

            @Override
            public void onCatchup() {
                observer.onCatchup();
            }

            @Override
            public void onComplete() {
                observer.onComplete();
            }

            @Override
            public void onError(Throwable e) {
                observer.onError(e);
            }
        });
    }

    @Override
    @NonNull
    public Optional<Fact> fetchById(UUID id) {
        return lookup.lookup(id);
    }

    @Override
    @NonNull
    public OptionalLong serialOf(@NonNull UUID ids) {
        return delegate.serialOf(ids);
    }

}
