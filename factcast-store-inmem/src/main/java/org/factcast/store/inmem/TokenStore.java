package org.factcast.store.inmem;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.factcast.core.store.StateToken;

interface TokenStore {
    StateToken create(Map<UUID, Optional<UUID>> state);

    void invalidate(StateToken token);

    Map<UUID, Optional<UUID>> get(StateToken token);
}