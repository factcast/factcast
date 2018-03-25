package org.factcast.client.cache;

import java.util.Optional;
import java.util.UUID;

import org.factcast.core.Fact;
import org.factcast.core.store.FactStore;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * A cacheable wrapper for a lookup of facts by their id.
 * 
 * Not intended for direct usage from with application code. This is used by the
 * CachingFactCast wrapper as a strategy to lookup facts.
 * 
 * @author <uwe.schaefer@mercateo.com>
 *
 */
@Component
@RequiredArgsConstructor
public class CachingFactLookup {

    public static final String CACHE_NAME = "factcast.lookup.fact";

    @NonNull
    final FactStore store;

    @Cacheable(CACHE_NAME)
    public Optional<Fact> lookup(UUID id) {
        return store.fetchById(id);
    }
}
