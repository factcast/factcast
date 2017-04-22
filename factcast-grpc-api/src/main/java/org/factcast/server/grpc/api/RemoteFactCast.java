package org.factcast.server.grpc.api;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.factcast.core.Fact;
import org.factcast.core.store.subscription.Subscription;
import org.factcast.core.store.subscription.SubscriptionRequest;
import org.factcast.core.wellknown.MarkFact;

import com.google.common.collect.Lists;

import lombok.NonNull;

public interface RemoteFactCast {
	CompletableFuture<Subscription> subscribe(@NonNull SubscriptionRequest req, @NonNull FactObserver observer);

	CompletableFuture<Subscription> subscribe(@NonNull SubscriptionRequest req, @NonNull IdObserver observer);

	Optional<Fact> fetchById(@NonNull UUID id);

	void publish(@NonNull Collection<Fact> factsToPublish);

	/// ---------- defaults

	default void publish(@NonNull Fact factToPublish) {
		publish(toList(factToPublish));
	}

	default UUID publishWithMark(@NonNull Fact factToPublish) {
		MarkFact m = new MarkFact();
		publish(toList(factToPublish, m));
		return m.id();
	}

	default UUID publishWithMark(@NonNull Collection<Fact> factsToPublish) {
		MarkFact m = new MarkFact();
		publish(toList(factsToPublish, m));
		return m.id();
	}

	// async

	default CompletableFuture<UUID> publishAsyncWithMark(@NonNull Collection<Fact> factsToPublish) {
		return CompletableFuture.supplyAsync(() -> publishWithMark(factsToPublish));
	}

	default CompletableFuture<UUID> publishAsyncWithMark(@NonNull Fact factToPublish) {
		return CompletableFuture.supplyAsync(() -> publishWithMark(factToPublish));
	}

	default CompletableFuture<Void> publishAsync(@NonNull Fact factToPublish) {
		return CompletableFuture.runAsync(() -> publish(factToPublish));
	}

	default CompletableFuture<Void> publishAsync(@NonNull Collection<Fact> factsToPublish) {
		return CompletableFuture.runAsync(() -> publish(factsToPublish));
	}

	// helpers

	static List<Fact> toList(Fact f) {
		return Arrays.asList(f);
	}

	static List<Fact> toList(Fact f, MarkFact m) {
		return toList(Arrays.asList(f), m);
	}

	static List<Fact> toList(Collection<Fact> f, MarkFact m) {
		LinkedList<Fact> newLinkedList = Lists.newLinkedList(f);
		newLinkedList.add(m);
		return newLinkedList;
	}
}
