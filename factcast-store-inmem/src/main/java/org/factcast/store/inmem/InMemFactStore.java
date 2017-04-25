package org.factcast.store.inmem;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.factcast.core.Fact;
import org.factcast.core.store.FactStore;
import org.factcast.core.subscription.FactSpecMatcher;
import org.factcast.core.subscription.FactStoreObserver;
import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.springframework.beans.factory.DisposableBean;

import com.google.common.annotations.VisibleForTesting;

import lombok.NonNull;

/**
 * Eternally-growing InMem Implementation of a FactStore. USE FOR TESTING
 * PURPOSES ONLY
 * 
 * @author usr
 *
 */
@Deprecated
public class InMemFactStore implements FactStore, DisposableBean {

	@VisibleForTesting
	InMemFactStore(@NonNull ExecutorService es) {
		this.es = es;
	}

	public InMemFactStore() {
		this(Executors.newCachedThreadPool());
	}

	private final AtomicInteger highwaterMark = new AtomicInteger(0);
	private final LinkedHashMap<Integer, Fact> store = new LinkedHashMap<>();
	private final CopyOnWriteArrayList<InMemSubscription> sub = new CopyOnWriteArrayList<>();
	private final ExecutorService es;

	private class InMemSubscription implements Subscription, Consumer<Fact> {
		private final Predicate<Fact> matcher;
		final Consumer<Fact> consumer;

		InMemSubscription(SubscriptionRequestTO req, Consumer<Fact> consumer) {
			this.consumer = consumer;
			matcher = FactSpecMatcher.matchesAnyOf(req.specs());
		}

		@Override
		public void close() {
			synchronized (InMemFactStore.this) {
				sub.remove(this);
			}
		}

		public boolean matches(Fact f) {
			return matcher.test(f);
		}

		@Override
		public void accept(Fact t) {
			if (matches(t)) {
				consumer.accept(t);
			}
		}

	}

	@Override
	public synchronized Optional<Fact> fetchById(@NonNull UUID id) {
		Stream<Entry<Integer, Fact>> stream = store.entrySet().stream();
		return stream.filter(e -> e.getValue().id().equals(id)).findFirst().map(e -> e.getValue());
	}

	@Override
	public synchronized void publish(@NonNull List<? extends Fact> factsToPublish) {
		factsToPublish.forEach(f -> {
			int ser = highwaterMark.incrementAndGet();
			store.put(ser, f);

			sub.parallelStream().forEach(s -> es.submit(() -> s.accept(f)));
		});
	}

	@Override
	public synchronized CompletableFuture<Subscription> subscribe(SubscriptionRequestTO req,
			FactStoreObserver observer) {

		InMemSubscription s = new InMemSubscription(req, c -> observer.onNext(c));
		if (!req.ephemeral()) {
			store.values().stream().forEach(s);
		}

		observer.onCatchup();

		if (req.continous()) {
			sub.add(s);
			return CompletableFuture.completedFuture(s);
		} else {
			observer.onComplete();
			return CompletableFuture.completedFuture(() -> {
			});
		}

	}

	@Override
	public synchronized void destroy() throws Exception {
		es.shutdown();
	}

}
