package org.factcast.client.cache;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.factcast.core.Fact;
import org.factcast.core.FactCast;
import org.factcast.core.subscription.FactObserver;
import org.factcast.core.subscription.IdObserver;
import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.SubscriptionRequest;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Integrates local cache for Facts.
 * 
 * Note that this Wrapper does necessarily not make your consumers faster, as
 * the local caching (might involve IO!) and the increased network activity will
 * have significant impact. Local caching is a good strategy, read the same
 * Event very often by either restarting Views using non-snapshot transient
 * views, or have many local consumers that share interest in the same Facts.
 * 
 * @author usr
 *
 */

@RequiredArgsConstructor
public class CachingFactCast implements FactCast {

	final FactCast delegate;
	final CachingFactLookup lookup;

	@Override
	public void publish(@NonNull List<? extends Fact> factsToPublish) {
		delegate.publish(factsToPublish);
	}

	@Override
	public CompletableFuture<Subscription> subscribeToIds(SubscriptionRequest req, IdObserver observer) {
		return delegate.subscribeToIds(req, observer);
	}

	@Override
	public CompletableFuture<Subscription> subscribeToFacts(SubscriptionRequest req, FactObserver observer) {
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

	public Optional<Fact> fetchById(UUID id) {
		return Optional.ofNullable(lookup.lookup(id));
	}

}
