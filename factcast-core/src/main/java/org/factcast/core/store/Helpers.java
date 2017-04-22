package org.factcast.core.store;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.factcast.core.Fact;
import org.factcast.core.wellknown.MarkFact;

import com.google.common.collect.Lists;

import lombok.NonNull;

/**
 * Helpers to be used from default methods of FactStore.
 * 
 * Extracted to keep the interface clean.
 * 
 * @author usr
 *
 */
class Helpers {

	static List<Fact> toList(@NonNull Fact f) {
		return Arrays.asList(f);
	}

	static List<Fact> toList(@NonNull Fact f, @NonNull MarkFact m) {
		return toList(toList(f), m);
	}

	static List<Fact> toList(@NonNull Iterable<Fact> f, @NonNull MarkFact m) {
		LinkedList<Fact> newLinkedList = Lists.newLinkedList(f);
		newLinkedList.add(m);
		return newLinkedList;
	}
}