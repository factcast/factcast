package org.factcast.core;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.factcast.core.store.FactStore;
import org.factcast.core.wellknown.MarkFact;

import lombok.NonNull;

/**
 * Main interface to work against as a client.
 * 
 * FactCast provides methods to publish Facts in a sync/async fashion, as well
 * as a subscription mechanism to listen for changes and catching up.
 * 
 * @author usr
 *
 */
public interface FactCast extends ReadFactCast {

	void publish(@NonNull List<? extends Fact> factsToPublish);

	/// ---------- defaults

	default void publish(@NonNull Fact factToPublish) {
		publish(Helpers.toList(factToPublish));
	}

	default UUID publishWithMark(@NonNull Fact factToPublish) {
		MarkFact m = new MarkFact();
		publish(Helpers.toList(factToPublish, m));
		return m.id();
	}

	default UUID publishWithMark(@NonNull List<Fact> factsToPublish) {
		MarkFact m = new MarkFact();
		publish(Helpers.toList(factsToPublish, m));
		return m.id();
	}

	// async

	default CompletableFuture<UUID> publishAsyncWithMark(@NonNull List<Fact> factsToPublish) {
		return CompletableFuture.supplyAsync(() -> publishWithMark(factsToPublish));
	}

	default CompletableFuture<UUID> publishAsyncWithMark(@NonNull Fact factToPublish) {
		return CompletableFuture.supplyAsync(() -> publishWithMark(factToPublish));
	}

	default CompletableFuture<Void> publishAsync(@NonNull Fact factToPublish) {
		return CompletableFuture.runAsync(() -> publish(factToPublish));
	}

	default CompletableFuture<Void> publishAsync(@NonNull List<Fact> factsToPublish) {
		return CompletableFuture.runAsync(() -> publish(factsToPublish));
	}

	// Factory

	public static FactCast from(FactStore store) {
		return new DefaultFactCast(store);
	}

	public static ReadFactCast fromReadOnly(FactStore store) {
		return new DefaultFactCast(store);
	}

}
