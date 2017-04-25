package org.factcast.client.cache;

import java.util.Optional;
import java.util.UUID;

import org.factcast.core.Fact;
import org.factcast.core.store.FactStore;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CachingFactLookup {

    public static final String CACHE_NAME = "factcast.lookup.fact";

    @NonNull
    private final FactStore store;

    @Cacheable(CACHE_NAME)
    public Optional<Fact> lookup(@NonNull UUID id) {
        return store.fetchById(id);
    }
}
