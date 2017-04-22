package org.factcast.core;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.factcast.core.store.FactStore;
import org.factcast.core.store.subscription.FactStoreObserver;
import org.factcast.core.store.subscription.Subscription;
import org.factcast.core.store.subscription.SubscriptionRequest;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
class DefaultFactCast implements FactCast {

	@NonNull
	final FactStore store;

	@Override
	public CompletableFuture<Subscription> subscribe(@NonNull SubscriptionRequest req, @NonNull FactObserver observer) {
		return store.subscribe(req, new ObserverBridge<Fact>(observer, Function.identity()));
	}

	@Override
	public CompletableFuture<Subscription> subscribe(@NonNull SubscriptionRequest req, @NonNull IdObserver observer) {
		return store.subscribe(req, new ObserverBridge<UUID>(observer, f -> f.id()));
	}

	@Override
	public Optional<Fact> fetchById(@NonNull UUID id) {
		return store.fetchById(id);
	}

	@Override
	public void publish(@NonNull List<Fact> factsToPublish) {
		store.publish(factsToPublish);
	}

	@RequiredArgsConstructor
	static class ObserverBridge<T> implements FactStoreObserver {

		private final GenericObserver<T> delegate;
		private final Function<Fact, T> project;

		@Override
		public void onNext(Fact f) {
			delegate.onNext(project.apply(f));
		}

		@Override
		public void onCatchup() {
			delegate.onCatchup();
		}

		@Override
		public void onError(Throwable e) {
			delegate.onError(e);
		}

		@Override
		public void onComplete() {
			delegate.onComplete();
		}
	}
}
