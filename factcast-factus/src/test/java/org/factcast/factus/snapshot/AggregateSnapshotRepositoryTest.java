/*
 * Copyright © 2017-2020 factcast.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.factcast.factus.snapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import lombok.NonNull;
import org.factcast.factus.projection.Aggregate;
import org.junit.jupiter.api.Test;

class AggregateSnapshotRepositoryTest {

  @Test
  void putBlocking() throws ExecutionException, InterruptedException {
    // INIT
    CompletableFuture<Void> future = mock(CompletableFuture.class);
    AtomicReference<Aggregate> aggregateHolder = new AtomicReference<>();
    AtomicReference<UUID> stateHolder = new AtomicReference<>();

    AggregateSnapshotRepository underTest =
        new AggregateSnapshotRepository() {
          @Override
          public Optional<Snapshot> findLatest(
              @NonNull Class<? extends Aggregate> type, @NonNull UUID aggregateId) {
            return Optional.empty();
          }

          @Override
          public CompletableFuture<Void> put(Aggregate aggregate, UUID state) {
            aggregateHolder.set(aggregate);
            stateHolder.set(state);
            return future;
          }
        };

    UUID state = UUID.randomUUID();
    Aggregate mockedAggregate = mock(Aggregate.class);

    // RUN
    underTest.putBlocking(mockedAggregate, state);

    // ASSERT
    assertThat(aggregateHolder).hasValue(mockedAggregate);

    assertThat(stateHolder).hasValue(state);

    verify(future).get();
  }
}
