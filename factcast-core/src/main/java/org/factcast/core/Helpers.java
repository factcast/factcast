package org.factcast.core;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.factcast.core.wellknown.MarkFact;

import com.google.common.collect.Lists;

import lombok.NonNull;

class Helpers {

	public static List<Fact> toList(@NonNull Fact f) {
		return Arrays.asList(f);
	}

	public static List<Fact> toList(@NonNull Fact f, @NonNull MarkFact m) {
		return toList(Arrays.asList(f), m);
	}

	public static List<Fact> toList(@NonNull List<Fact> f, @NonNull MarkFact m) {
		LinkedList<Fact> newLinkedList = Lists.newLinkedList(f);
		newLinkedList.add(m);
		return newLinkedList;
	}

}
