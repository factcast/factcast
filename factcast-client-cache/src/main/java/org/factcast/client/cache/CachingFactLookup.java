package org.factcast.client.cache;

import java.util.UUID;

import org.factcast.core.Fact;
import org.factcast.core.store.FactStore;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j

public class CachingFactLookup {

	public static final String CACHE_NAME = "factcast.lookup.fact";

	@NonNull
	private final FactStore store;

	@Cacheable(CACHE_NAME)
	public Fact lookup(@NonNull UUID id) {
		return store.fetchById(id).orElse(null);
	}
}
