package org.factcast.factus.lock;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;

import org.factcast.core.FactCast;
import org.factcast.core.lock.LockedOperationBuilder;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.store.FactStore;
import org.factcast.core.store.StateToken;
import org.factcast.factus.Factus;
import org.factcast.factus.metrics.FactusMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Component test of Locked and WithOptimisticLock.
 */
@ExtendWith(MockitoExtension.class)
class LockedTestSnapshotProjection {

    @Mock
    private FactCast fc;

    @Mock
    private Factus factus;

    @Mock
    private List<FactSpec> factSpecs;

    @Mock
    private FactusMetrics factusMetrics;

    @Spy
    private NamesSnapshotProjection namesSnapshotProjection;

    @InjectMocks
    private Locked<NamesSnapshotProjection> underTest;

    // further mocks needed

    @Mock
    private FactStore factStore;

    @Mock
    private StateToken noEvents;

    @Mock
    private StateToken firstEvent;

    @BeforeEach
    void mockFactCast() {
        when(fc.lock(factSpecs))
                .thenReturn(new LockedOperationBuilder(factStore, factSpecs));
    }

    @Test
    void attemptSuccess() {
        // INIT
        // first time querying state: no facts yet
        when(factStore.stateFor(factSpecs))
                .thenReturn(noEvents);

        // publishing went through without any problems
        when(factStore.publishIfUnchanged(any(), any()))
                .thenReturn(true);

        when(factus.fetch(NamesSnapshotProjection.class))
                .thenReturn(namesSnapshotProjection);

        BiConsumer<NamesSnapshotProjection, RetryableTransaction> businessCode = spy(
                // cannot be lambda, as we cannot spy on it otherwise
                new BiConsumer<NamesSnapshotProjection, RetryableTransaction>() {
                    @Override
                    public void accept(NamesSnapshotProjection projection,
                            RetryableTransaction tx) {
                        if (!namesSnapshotProjection.contains("Peter")) {
                            tx.publish(new UserCreated(UUID.randomUUID(), "Peter"));
                        } else {
                            tx.abort("Peter already exists");
                        }
                    }
                });

        // RUN
        underTest.attempt(businessCode);

        // ASSERT
        InOrder inOrder = inOrder(factus, businessCode, factStore);

        // verify that first, projection got updated...
        inOrder.verify(factus)
                .fetch(NamesSnapshotProjection.class);
        // ... then our business code was run...
        inOrder.verify(businessCode)
                .accept(any(), any());
        // ... and then we published things
        inOrder.verify(factStore)
                .publishIfUnchanged(any(), any());
    }

}