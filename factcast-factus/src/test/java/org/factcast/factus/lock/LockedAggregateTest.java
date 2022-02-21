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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Objects;
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
import org.factcast.factus.projection.AggregateUtil;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

/** Component test of Locked and WithOptimisticLock. */
@ExtendWith(MockitoExtension.class)
class LockedAggregateTest {

  @Mock private FactCast fc;

  @Mock private Factus factus;

  @Mock private List<FactSpec> factSpecs;

  @Mock private FactusMetrics factusMetrics;

  @Spy private UserAggregate userAggregate;

  @InjectMocks private Locked<UserAggregate> underTest;

  // further mocks needed

  @Mock private FactStore factStore;

  @Mock private StateToken noEvents;

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

    UUID aggId = UUID.randomUUID();

    when(factus.fetch(UserAggregate.class, aggId)).thenReturn(userAggregate);

    AggregateUtil.aggregateId(userAggregate, aggId);
    userAggregate.name("Peter");

    BiConsumer<UserAggregate, RetryableTransaction> businessCode =
        spy(
            // cannot be lambda, as we cannot spy on it otherwise
            new BiConsumer<UserAggregate, RetryableTransaction>() {
              @Override
              public void accept(UserAggregate projection, RetryableTransaction tx) {
                if (Objects.equals(projection.name(), "Peter")) {
                  tx.publish(new UserDeleted(aggId));
                } else {
                  tx.abort("Peter not found");
                }
              }
            });

    Fact mockedUserDeletedFact = mock(Fact.class);
    when(factus.toFact(any(UserDeleted.class))).thenReturn(mockedUserDeletedFact);

    // RUN
    underTest.attempt(businessCode);

    // ASSERT
    InOrder inOrder = inOrder(factus, businessCode, factStore);

    // verify that first, projection got updated...
    inOrder.verify(factus).fetch(UserAggregate.class, aggId);
    // ... then our business code was run...
    inOrder.verify(businessCode).accept(any(), any());
    // ... and then we published things
    inOrder.verify(factStore).publishIfUnchanged(factListCaptor.capture(), tokenCaptor.capture());

    assertThat(factListCaptor.getValue()).hasSize(1);

    assertThat(factListCaptor.getValue().get(0)).isEqualTo(mockedUserDeletedFact);

    assertThat(tokenCaptor.getValue()).isPresent().get().isEqualTo(noEvents);
  }
}
