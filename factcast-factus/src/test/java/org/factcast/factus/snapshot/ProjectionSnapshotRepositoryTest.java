package org.factcast.factus.snapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

import org.factcast.core.snap.Snapshot;
import org.factcast.factus.projection.Aggregate;
import org.factcast.factus.projection.SnapshotProjection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import lombok.NonNull;

@ExtendWith(MockitoExtension.class)
class ProjectionSnapshotRepositoryTest {

    @Test
    void putBlocking() throws ExecutionException, InterruptedException {
        // INIT
        CompletableFuture<Void> future = mock(CompletableFuture.class);
        AtomicReference<SnapshotProjection> projectionHolder = new AtomicReference<>();
        AtomicReference<UUID> stateHolder = new AtomicReference<>();

        ProjectionSnapshotRepository underTest = new ProjectionSnapshotRepository() {
            @Override
            public Optional<Snapshot> findLatest(
                    @NonNull Class<? extends SnapshotProjection> type) {
                return Optional.empty();
            }

            @Override
            public CompletableFuture<Void> put(SnapshotProjection projection, UUID state) {
                projectionHolder.set(projection);
                stateHolder.set(state);
                return future;
            }
        };

        UUID state = UUID.randomUUID();
        Aggregate mockedAggregate = mock(Aggregate.class);

        // RUN
        underTest.putBlocking(mockedAggregate, state);

        // ASSERT
        assertThat(projectionHolder)
                .hasValue(mockedAggregate);

        assertThat(stateHolder)
                .hasValue(state);

        verify(future)
                .get();
    }

    @Nested
    class WhenFindingLatest {
        @Mock
        private @NonNull Class<? extends SnapshotProjection> type;

        @BeforeEach
        void setup() {
        }
    }

    @Nested
    class WhenPuting {
        private final UUID STATE = UUID.randomUUID();

        @Mock
        private SnapshotProjection projection;

        @BeforeEach
        void setup() {
        }
    }

    @Nested
    class WhenPutingBlocking {
        private final UUID STATE = UUID.randomUUID();

        @Mock
        private SnapshotProjection projection;

        @BeforeEach
        void setup() {
        }
    }
}