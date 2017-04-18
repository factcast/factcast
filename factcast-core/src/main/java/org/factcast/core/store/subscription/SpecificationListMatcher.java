package org.factcast.core.store.subscription;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.factcast.core.Fact;

public class SpecificationListMatcher implements Predicate<Fact> {

	private final List<FactSpecMatcher> matchers;

	@Override
	public boolean test(Fact t) {
		// TODO should this be a parallel stream, or would that in fact hurt
		// performance due to coordination overhead?
		return matchers.stream().anyMatch(m -> m.test(t));
	}

	public SpecificationListMatcher(List<FactSpec> specs) {
		matchers = specs.stream().map(s -> new FactSpecMatcher(s)).collect(Collectors.toList());
	}
}
