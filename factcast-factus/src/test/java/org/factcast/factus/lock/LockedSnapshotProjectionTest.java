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
package org.factcast.factus.lock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import org.factcast.core.Fact;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

/** Component test of Locked and WithOptimisticLock. */
@ExtendWith(MockitoExtension.class)
class LockedSnapshotProjectionTest {

  @Mock private FactCast fc;

  @Mock private Factus factus;

  @Mock private List<FactSpec> factSpecs;

  @Mock private FactusMetrics factusMetrics;

  @Spy private NamesSnapshotProjection namesSnapshotProjection;

  @InjectMocks private Locked<NamesSnapshotProjection> underTest;

  // further mocks needed

  @Mock private FactStore factStore;

  @Mock private StateToken noEvents;

  @Mock private StateToken firstEvent;

  @Captor private ArgumentCaptor<List<? extends Fact>> factListCaptor;

  @Captor private ArgumentCaptor<Optional<StateToken>> tokenCaptor;

  @BeforeEach
  void mockFactCast() {
    when(fc.lock(factSpecs)).thenReturn(new LockedOperationBuilder(factStore, factSpecs));
  }

  @Test
  void attemptSuccess() {
    // INIT
    // first time querying state: no facts yet
    when(factStore.currentStateFor(factSpecs)).thenReturn(noEvents);

    // publishing went through without any problems
    when(factStore.publishIfUnchanged(any(), any())).thenReturn(true);

    when(factus.fetch(NamesSnapshotProjection.class)).thenReturn(namesSnapshotProjection);

    BiConsumer<NamesSnapshotProjection, RetryableTransaction> businessCode =
        spy(
            // cannot be lambda, as we cannot spy on it otherwise
            new BiConsumer<NamesSnapshotProjection, RetryableTransaction>() {
              @Override
              public void accept(NamesSnapshotProjection projection, RetryableTransaction tx) {
                if (!namesSnapshotProjection.contains("Peter")) {
                  tx.publish(new UserCreated(UUID.randomUUID(), "Peter"));
                } else {
                  tx.abort("Peter already exists");
                }
              }
            });

    Fact mockedUserCreatedFact = mock(Fact.class);
    when(factus.toFact(any(UserCreated.class))).thenReturn(mockedUserCreatedFact);

    // RUN
    underTest.attempt(businessCode);

    // ASSERT
    InOrder inOrder = inOrder(factus, businessCode, factStore);

    // verify that first, projection got updated...
    inOrder.verify(factus).fetch(NamesSnapshotProjection.class);
    // ... then our business code was run...
    inOrder.verify(businessCode).accept(any(), any());
    // ... and then we published things
    inOrder.verify(factStore).publishIfUnchanged(factListCaptor.capture(), tokenCaptor.capture());

    assertThat(factListCaptor.getValue()).hasSize(1);

    assertThat(factListCaptor.getValue().get(0)).isEqualTo(mockedUserCreatedFact);

    assertThat(tokenCaptor.getValue()).isPresent().get().isEqualTo(noEvents);
  }
}
