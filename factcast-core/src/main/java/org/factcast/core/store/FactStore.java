package org.factcast.core.store;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.factcast.core.Fact;
import org.factcast.core.wellknown.MarkFact;

import com.google.common.collect.Lists;

import lombok.NonNull;

public interface FactStore extends ReadFactStore {

	void publish(@NonNull Iterable<Fact> factsToPublish);

	/// ---------- defaults

	default void publish(@NonNull Fact factToPublish) {
		publish(toList(factToPublish));
	}

	default UUID publishWithMark(@NonNull Fact factToPublish) {
		MarkFact m = new MarkFact();
		publish(toList(factToPublish, m));
		return m.id();
	}

	default UUID publishWithMark(@NonNull Iterable<Fact> factsToPublish) {
		MarkFact m = new MarkFact();
		publish(toList(factsToPublish, m));
		return m.id();
	}

	// async
	
	default CompletableFuture<UUID> publishAsyncWithMark(@NonNull Iterable<Fact> factsToPublish) {
		return CompletableFuture.supplyAsync(() -> publishWithMark(factsToPublish));
	}

	default CompletableFuture<UUID> publishAsyncWithMark(@NonNull Fact factToPublish) {
		return CompletableFuture.supplyAsync(() -> publishWithMark(factToPublish));
	}

	default CompletableFuture<Void> publishAsync(@NonNull Fact factToPublish) {
		return CompletableFuture.runAsync(() -> publish(factToPublish));
	}

	default CompletableFuture<Void> publishAsync(@NonNull Iterable<Fact> factsToPublish) {
		return CompletableFuture.runAsync(() -> publish(factsToPublish));
	}

	// helpers
	
	static List<Fact> toList(Fact f) {
		return Arrays.asList(f);
	}

	static List<Fact> toList(Fact f, MarkFact m) {
		return toList(Arrays.asList(f), m);
	}

	static List<Fact> toList(Iterable<Fact> f, MarkFact m) {
		LinkedList<Fact> newLinkedList = Lists.newLinkedList(f);
		newLinkedList.add(m);
		return newLinkedList;
	}
}
