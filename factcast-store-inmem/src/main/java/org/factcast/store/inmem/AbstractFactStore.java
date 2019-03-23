package org.factcast.store.inmem;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.UUID;

import org.factcast.core.Fact;
import org.factcast.core.store.FactStore;
import org.factcast.core.store.StateToken;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor

public abstract class AbstractFactStore implements FactStore {
    protected final TokenStore tokenStore;

    protected abstract Optional<UUID> latestFactFor(UUID aggId);

    @Override
    public boolean publishIfUnchanged(@NonNull StateToken token,
            @NonNull List<? extends Fact> factsToPublish) {
        if (isStateUnchanged(tokenStore.get(token))) {
            publish(factsToPublish);
            tokenStore.invalidate(token);
            return true;
        } else
            return false;
    }

    private boolean isStateUnchanged(@NonNull Map<UUID, Optional<UUID>> state) {
        for (Entry<UUID, Optional<UUID>> e : state.entrySet()) {
            if (!latestFactFor(e.getKey()).equals(e.getValue()))
                return false;
        }
        return true;
    }

}
