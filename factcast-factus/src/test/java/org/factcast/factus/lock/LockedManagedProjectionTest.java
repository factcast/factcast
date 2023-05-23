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

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import lombok.SneakyThrows;
import org.assertj.core.util.Lists;
import org.factcast.core.Fact;
import org.factcast.core.FactCast;
import org.factcast.core.lock.LockedOperationBuilder;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.store.FactStore;
import org.factcast.core.store.StateToken;
import org.factcast.factus.Factus;
import org.factcast.factus.metrics.FactusMetrics;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

/** Component test of Locked and WithOptimisticLock. */
@ExtendWith(MockitoExtension.class)
class LockedManagedProjectionTest {

  @Mock private FactCast fc;

  @Mock private Factus factus;

  @Mock private List<FactSpec> factSpecs;

  @Mock private FactusMetrics factusMetrics;

  @Spy private NamesProjection managedProjection;

  @InjectMocks private Locked<NamesProjection> underTest;

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

    BiConsumer<NamesProjection, RetryableTransaction> businessCode =
        spy(
            // cannot be lambda, as we cannot spy on it otherwise
            new BiConsumer<NamesProjection, RetryableTransaction>() {
              @Override
              public void accept(NamesProjection projection, RetryableTransaction tx) {
                if (!projection.contains("Peter")) {
                  tx.publish(new UserCreated(UUID.randomUUID(), "Peter"));
                } else {
                  tx.abort("Peter is already there");
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
    inOrder.verify(factus).update(managedProjection);
    // ... then our business code was run...
    inOrder.verify(businessCode).accept(any(), any());
    // ... and then we published things
    inOrder.verify(factStore).publishIfUnchanged(factListCaptor.capture(), tokenCaptor.capture());

    assertThat(factListCaptor.getValue()).hasSize(1);

    assertThat(factListCaptor.getValue().get(0)).isEqualTo(mockedUserCreatedFact);

    assertThat(tokenCaptor.getValue()).isPresent().get().isEqualTo(noEvents);
  }

  @Test
  void attemptSuccessListOfEventObjects() {
    // INIT
    // first time querying state: no facts yet
    when(factStore.currentStateFor(factSpecs)).thenReturn(noEvents);

    // publishing went through without any problems
    when(factStore.publishIfUnchanged(any(), any())).thenReturn(true);

    BiConsumer<NamesProjection, RetryableTransaction> businessCode =
        spy(
            // cannot be lambda, as we cannot spy on it otherwise
            new BiConsumer<NamesProjection, RetryableTransaction>() {
              @Override
              public void accept(NamesProjection projection, RetryableTransaction tx) {
                if (!projection.contains("Peter")) {
                  tx.publish(Lists.newArrayList(new UserCreated(UUID.randomUUID(), "Peter")));
                } else {
                  tx.abort("Peter is already there");
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
    inOrder.verify(factus).update(managedProjection);
    // ... then our business code was run...
    inOrder.verify(businessCode).accept(any(), any());
    // ... and then we published things
    inOrder.verify(factStore).publishIfUnchanged(factListCaptor.capture(), tokenCaptor.capture());

    assertThat(factListCaptor.getValue()).hasSize(1);

    assertThat(factListCaptor.getValue().get(0)).isEqualTo(mockedUserCreatedFact);

    assertThat(tokenCaptor.getValue()).isPresent().get().isEqualTo(noEvents);
  }

  @Test
  void attemptSuccessFact() throws Exception {
    // INIT
    // first time querying state: no facts yet
    when(factStore.currentStateFor(factSpecs)).thenReturn(noEvents);

    // publishing went through without any problems
    when(factStore.publishIfUnchanged(any(), any())).thenReturn(true);

    final Fact rawFact = Fact.builder().id(UUID.randomUUID()).buildWithoutPayload();

    BiConsumer<NamesProjection, RetryableTransaction> businessCode =
        spy(
            // cannot be lambda, as we cannot spy on it otherwise
            new BiConsumer<NamesProjection, RetryableTransaction>() {
              @SneakyThrows
              @Override
              public void accept(NamesProjection projection, RetryableTransaction tx) {

                // then test publish
                if (!projection.contains("Peter")) {
                  tx.publish(rawFact);
                } else {
                  tx.abort("Peter is already there");
                }
              }
            });

    // RUN
    underTest.attempt(businessCode);

    // ASSERT
    InOrder inOrder = inOrder(factus, businessCode, factStore);

    // verify that first, projection got updated...
    inOrder.verify(factus).update(managedProjection);
    // ... then our business code was run...
    inOrder.verify(businessCode).accept(any(), any());
    // ... and then we published things
    inOrder.verify(factStore).publishIfUnchanged(factListCaptor.capture(), tokenCaptor.capture());

    assertThat(factListCaptor.getValue()).hasSize(1);

    assertThat(factListCaptor.getValue().get(0)).isEqualTo(rawFact);

    assertThat(tokenCaptor.getValue()).isPresent().get().isEqualTo(noEvents);
  }

  /** tx provides more methods, like find or update. Make sure they reach factus properly */
  @Test
  void testTxWiresThroughToFactus() throws Exception {
    // INIT
    // first time querying state: no facts yet
    when(factStore.currentStateFor(factSpecs)).thenReturn(noEvents);

    // publishing went through without any problems
    when(factStore.publishIfUnchanged(any(), any())).thenReturn(true);

    NamesSnapshotProjection mockSnapshot = mock(NamesSnapshotProjection.class);
    when(factus.fetch(NamesSnapshotProjection.class)).thenReturn(mockSnapshot);

    UUID aggId = UUID.randomUUID();
    UserAggregate mockAggregate = mock(UserAggregate.class);
    when(factus.find(UserAggregate.class, aggId)).thenReturn(Optional.of(mockAggregate));

    AtomicReference<NamesSnapshotProjection> fetchResultHolder = new AtomicReference<>();
    AtomicReference<Optional<UserAggregate>> findResultHolder = new AtomicReference<>();

    NamesProjection otherProjection = mock(NamesProjection.class);

    BiConsumer<NamesProjection, RetryableTransaction> businessCode =
        spy(
            // cannot be lambda, as we cannot spy on it otherwise
            new BiConsumer<NamesProjection, RetryableTransaction>() {
              @SneakyThrows
              @Override
              public void accept(NamesProjection projection, RetryableTransaction tx) {

                // also test fetch, find and update
                fetchResultHolder.set(tx.fetch(NamesSnapshotProjection.class));
                findResultHolder.set(tx.find(UserAggregate.class, aggId));
                tx.update(otherProjection, Duration.ofSeconds(1));

                tx.publish(new UserCreated(aggId, "Peter"));
              }
            });

    // RUN
    underTest.attempt(businessCode);

    // ASSERT
    verify(factus).fetch(NamesSnapshotProjection.class);
    assertThat(fetchResultHolder).hasValue(mockSnapshot);

    verify(factus).find(UserAggregate.class, aggId);
    assertThat(findResultHolder).hasValue(Optional.of(mockAggregate));

    verify(factus).update(otherProjection, Duration.ofSeconds(1));
  }

  @Test
  void attemptAborted() {
    // INIT
    // first time querying state: no facts yet
    when(factStore.currentStateFor(factSpecs)).thenReturn(firstEvent);

    doAnswer(
            inv -> {
              managedProjection.handle(new UserCreated(UUID.randomUUID(), "Peter"));
              return null;
            })
        .when(factus)
        .update(managedProjection);

    BiConsumer<NamesProjection, RetryableTransaction> businessCode =
        spy(
            // cannot be lambda, as we cannot spy on it otherwise
            new BiConsumer<NamesProjection, RetryableTransaction>() {
              @Override
              public void accept(NamesProjection projection, RetryableTransaction tx) {
                if (!projection.contains("Peter")) {
                  tx.publish(new UserCreated(UUID.randomUUID(), "Peter"));
                } else {
                  tx.abort("Peter is already there");
                }
              }
            });

    // RUN
    assertThatThrownBy(() -> underTest.attempt(businessCode))
        .isInstanceOf(LockedOperationAbortedException.class)
        .hasMessageContaining("Peter is already there");

    // ASSERT
    InOrder inOrder = inOrder(factus, businessCode, factStore);

    // verify that first, projection got updated...
    inOrder.verify(factus).update(managedProjection);
    // ... then our business code was run
    inOrder.verify(businessCode).accept(any(), any());

    // however, things should never have gotten published
    inOrder.verify(factStore, never()).publishIfUnchanged(any(), any());
  }
}
