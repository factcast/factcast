package org.factcast.core;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.UUID;

import org.factcast.core.store.FactStore;
import org.factcast.core.store.StateToken;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class LockedOperationBuilder {
    final FactStore store;

    public final WithOptimisticLock optimistic(@NonNull UUID aggId, UUID... otherAggIds) {
        LinkedList<UUID> ids = new LinkedList<>();
        ids.add(aggId);
        if (otherAggIds != null)
            ids.addAll(Arrays.asList(otherAggIds));

        StateToken token = store.stateFor(ids);

        return new WithOptimisticLock(store, token);
    }
}
