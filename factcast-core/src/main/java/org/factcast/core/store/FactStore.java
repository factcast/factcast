package org.factcast.core.store;

import java.util.UUID;

import org.factcast.core.Fact;
import org.factcast.core.wellknown.MarkFact;

import lombok.NonNull;

/**
 * A read/Write FactStore.
 * 
 * @author usr
 *
 */
public interface FactStore extends ReadFactStore {

	void publish(@NonNull Iterable<Fact> factsToPublish);

	/// ---------- defaults

	default void publish(@NonNull Fact factToPublish) {
		publish(Helpers.toList(factToPublish));
	}

	default UUID publishWithMark(@NonNull Fact factToPublish) {
		MarkFact m = new MarkFact();
		publish(Helpers.toList(factToPublish, m));
		return m.id();
	}

	default UUID publishWithMark(@NonNull Iterable<Fact> factsToPublish) {
		MarkFact m = new MarkFact();
		publish(Helpers.toList(factsToPublish, m));
		return m.id();
	}

}
