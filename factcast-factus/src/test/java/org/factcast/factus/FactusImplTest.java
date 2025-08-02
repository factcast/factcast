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
package org.factcast.factus;

import static java.util.UUID.fromString;
import static java.util.UUID.randomUUID;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.google.common.collect.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.*;
import java.util.stream.Collectors;
import lombok.*;
import org.assertj.core.api.Assertions;
import org.factcast.core.*;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.subscription.*;
import org.factcast.core.subscription.observer.FactObserver;
import org.factcast.factus.batch.*;
import org.factcast.factus.event.*;
import org.factcast.factus.event.EventObject;
import org.factcast.factus.lock.*;
import org.factcast.factus.lock.Locked;
import org.factcast.factus.metrics.*;
import org.factcast.factus.projection.*;
import org.factcast.factus.projection.parameter.HandlerParameterContributors;
import org.factcast.factus.projector.*;
import org.factcast.factus.serializer.*;
import org.factcast.factus.snapshot.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FactusImplTest {

  @Mock private FactCast fc;

  @Mock private ProjectorFactory ehFactory;

  @Mock private EventConverter eventConverter;

  @Mock private AggregateRepository aggregateSnapshotRepository;

  @Mock private SnapshotRepository projectionSnapshotRepository;

  @Mock SnapshotCache snapshotCache;

  @Mock private SnapshotSerializerSelector snapFactory;

  @Mock private AtomicBoolean closed;

  @Mock(strictness = Mock.Strictness.LENIENT)
  private WriterToken token;

  @Spy private final FactusMetrics factusMetrics = new FactusMetricsImpl(new SimpleMeterRegistry());

  @InjectMocks private FactusImpl underTest;

  @Captor ArgumentCaptor<FactObserver> factObserverCaptor;

  @Spy List<FactSpec> specs = Lists.newArrayList(FactSpec.ns("ns").type("type"));

  @BeforeEach
  void setup() {
    when(token.isValid()).thenReturn(true);
  }

  @Test
  void testToFact() {
    // INIT
    Fact mockedFact = mock(Fact.class);
    EventObject mockedEventObject = mock(EventObject.class);

    when(eventConverter.toFact(mockedEventObject)).thenReturn(mockedFact);

    // RUN
    Fact fact = underTest.toFact(mockedEventObject);

    // ASSERT
    assertThat(fact).isEqualTo(mockedFact);

    verify(eventConverter).toFact(mockedEventObject);
  }

  @Test
  void testSerialOf() {
    // ARRANGE
    UUID id = randomUUID();
    OptionalLong ret = OptionalLong.of(7);
    when(fc.serialOf(any())).thenReturn(ret);

    // ACT
    OptionalLong optSerial = underTest.serialOf(id);

    // ASSERT
    assertThat(optSerial).isSameAs(ret);
    verify(fc).serialOf(id);
  }

  @Nested
  class WhenPublishing {

    @Captor private ArgumentCaptor<Fact> factCaptor;

    @Captor private ArgumentCaptor<List<Fact>> factListCaptor;

    @Test
    void publishFact() {
      // INIT
      Fact fact = toFact(new SimpleEventObject("a"));

      // RUN
      underTest.publish(fact);

      // ASSERT
      verify(fc).publish(fact);
    }

    @Test
    void publishFactAfterClosed() {
      // INIT
      Fact fact = toFact(new SimpleEventObject("a"));

      underTest.close();

      // RUN
      assertThatThrownBy(() -> underTest.publish(fact))
          // ASSERT
          .isExactlyInstanceOf(IllegalStateException.class);
    }

    @Test
    void publishFactWhileInLockedOperation() {
      // INIT
      Fact fact = toFact(new SimpleEventObject("a"));

      try {
        InLockedOperation.enterLockedOperation();

        // RUN
        assertThatThrownBy(() -> underTest.publish(fact))
            // ASSERT
            .isExactlyInstanceOf(IllegalStateException.class);

      } finally {
        InLockedOperation.exitLockedOperation();
      }
    }

    @Test
    void publishEventObject() {
      // INIT
      EventObject eventObject = new SimpleEventObject("a");

      mockEventConverter();

      // RUN
      underTest.publish(eventObject);

      // ASSERT
      verify(fc).publish(factCaptor.capture());

      assertThatJson(factCaptor.getValue().jsonPayload()).and(f -> f.node("val").isEqualTo("a"));
    }

    @Test
    void publishEventObjectWithFunction() {
      // INIT
      EventObject eventObject = new SimpleEventObject("a");

      mockEventConverter();

      // RUN
      String jsonPayload = underTest.publish(eventObject, Fact::jsonPayload);

      // ASSERT
      verify(fc).publish(factCaptor.capture());

      assertThatJson(factCaptor.getValue().jsonPayload()).and(f -> f.node("val").isEqualTo("a"));

      assertThatJson(jsonPayload).and(f -> f.node("val").isEqualTo("a"));
    }

    @Test
    void publishEventObjectList() {
      // INIT
      List<EventObject> eventObjects =
          Lists.newArrayList(new SimpleEventObject("a"), new SimpleEventObject("b"));

      mockEventConverter();

      // RUN
      underTest.publish(eventObjects);

      // ASSERT
      verify(fc).publish(factListCaptor.capture());

      assertThat(factListCaptor.getValue())
          .anySatisfy(
              fact -> assertThatJson(fact.jsonPayload()).and(f -> f.node("val").isEqualTo("a")))
          .anySatisfy(
              fact -> assertThatJson(fact.jsonPayload()).and(f -> f.node("val").isEqualTo("b")));
    }

    @Test
    void publishEventObjectListWithFunction() {
      // INIT
      List<EventObject> eventObjects =
          Lists.newArrayList(new SimpleEventObject("a"), new SimpleEventObject("b"));

      mockEventConverter();

      // RUN
      List<String> jsonPayloads =
          underTest.publish(
              eventObjects,
              list -> list.stream().map(Fact::jsonPayload).collect(Collectors.toList()));

      // ASSERT
      verify(fc).publish(factListCaptor.capture());

      assertThat(factListCaptor.getValue())
          .anySatisfy(
              fact -> assertThatJson(fact.jsonPayload()).and(f -> f.node("val").isEqualTo("a")))
          .anySatisfy(
              fact -> assertThatJson(fact.jsonPayload()).and(f -> f.node("val").isEqualTo("b")));

      assertThat(jsonPayloads)
          .anySatisfy(fact -> assertThatJson(fact).and(f -> f.node("val").isEqualTo("a")))
          .anySatisfy(fact -> assertThatJson(fact).and(f -> f.node("val").isEqualTo("b")));
    }
  }

  @Nested
  class WhenBatching {

    @Captor private ArgumentCaptor<List<Fact>> factListCaptor;

    @Test
    void simpleBatch() {

      // INIT
      mockEventConverter();

      // RUN
      try (PublishBatch batch = underTest.batch()) {
        batch
            // let's mix event objects and facts in one batch
            .add(new SimpleEventObject("a"))
            .add(toFact(new SimpleEventObject("b")));
      }

      // ASSERT
      verify(fc).publish(factListCaptor.capture());

      assertThat(factListCaptor.getValue())
          .anySatisfy(
              fact -> assertThatJson(fact.jsonPayload()).and(f -> f.node("val").isEqualTo("a")))
          .anySatisfy(
              fact -> assertThatJson(fact.jsonPayload()).and(f -> f.node("val").isEqualTo("b")));
    }

    @Test
    void batchFailsIfExecutedTwice() {
      // INIT
      PublishBatch batch = underTest.batch();
      batch.add(new SimpleEventObject("a"));

      // should be fine
      batch.execute();

      // RUN
      // execute batch a second time without adding anzthing
      assertThatThrownBy(batch::execute)
          // ASSERT
          .isExactlyInstanceOf(IllegalStateException.class)
          .hasMessage("Has already been executed");
    }

    @Test
    void batchAbortedWithErrorMessage() {
      assertThatThrownBy(
              () -> {
                // RUN
                try (PublishBatch batch = underTest.batch()) {
                  batch
                      .add(new SimpleEventObject("a"))
                      .add(new SimpleEventObject("b"))
                      .markAborted("Problem, batch aborted!");
                }
              })
          // ASSERT
          .isExactlyInstanceOf(BatchAbortedException.class)
          .hasMessage("Problem, batch aborted!");
    }

    @Test
    void batchAbortedWithException() {
      assertThatThrownBy(
              () -> {
                // RUN
                try (PublishBatch batch = underTest.batch()) {
                  batch
                      .add(new SimpleEventObject("a"))
                      .add(new SimpleEventObject("b"))
                      .markAborted(new IllegalStateException("Problem, batch aborted!"));
                }
              })
          // ASSERT
          .isExactlyInstanceOf(BatchAbortedException.class)
          .hasCauseExactlyInstanceOf(IllegalStateException.class)
          .hasMessage("java.lang.IllegalStateException: Problem, batch aborted!");
    }
  }

  @Nested
  class WhenUpdating {

    @Test
    void updateIsExecutedViaProjection() {

      ManagedProjection m = Mockito.spy(new SimpleProjection());
      EventSerializer serializer = mock(EventSerializer.class);
      Projector<ManagedProjection> ea =
          Mockito.spy(new ProjectorImpl<>(m, serializer, new HandlerParameterContributors()));
      when(ehFactory.create(m)).thenReturn(ea);

      Fact f1 = Fact.builder().ns("test").type(SimpleEvent.class.getSimpleName()).build("{}");
      Fact f2 = Fact.builder().ns("test").type(SimpleEvent.class.getSimpleName()).build("{}");

      when(fc.subscribe(any(), any()))
          .thenAnswer(
              inv -> {
                FactObserver obs = (FactObserver) inv.getArgument(1);
                obs.onNext(f1);
                obs.onNext(f2);
                obs.flush();

                return Mockito.mock(Subscription.class);
              });
      underTest.update(m);

      // make sure m.executeUpdate actually calls the updated passed so
      // that
      // the prepared update happens on the projection and updates its
      // fact stream position.

      Mockito.verify(ea, times(1)).apply(any(List.class));
      assertThat(m.factStreamPosition()).isEqualTo(FactStreamPosition.from(f2));
    }
  }

  @Nested
  class WhenConditionallyPublishing {

    @Test
    void withLockOnSingleSpec() {
      // INIT
      @NonNull FactSpec spec = FactSpec.ns("ns").type("type");

      // RUN
      LockedOnSpecs locked = underTest.withLockOn(spec);

      // ASSERT

      assertThat(locked.factus()).isEqualTo(underTest);

      assertThat(locked.fc()).isEqualTo(fc);

      // this is important; if they are not the specs for the given
      // projection,
      // the lock would be broken
      assertThat(locked.specs()).hasSize(1).containsExactly(spec);
    }

    @Test
    void withLockOnSpecs() {
      // INIT
      FactSpec spec1 = FactSpec.ns("ns").type("type1");
      FactSpec spec2 = FactSpec.ns("ns").type("type2");

      // RUN
      LockedOnSpecs locked = underTest.withLockOn(spec1, spec2);

      // ASSERT
      assertThat(locked.factus()).isEqualTo(underTest);
      assertThat(locked.fc()).isEqualTo(fc);

      // this is important; if they are not the specs for the given
      // projection,
      // the lock would be broken
      assertThat(locked.specs()).hasSize(2).containsExactlyInAnyOrder(spec1, spec2);
    }

    @Test
    void withLockOnListOfSpecs() {
      // INIT
      FactSpec spec1 = FactSpec.ns("ns").type("type1");
      FactSpec spec2 = FactSpec.ns("ns").type("type2");

      // RUN
      LockedOnSpecs locked = underTest.withLockOn(Lists.newArrayList(spec1, spec2));

      // ASSERT
      assertThat(locked.factus()).isEqualTo(underTest);
      assertThat(locked.fc()).isEqualTo(fc);

      // this is important; if they are not the specs for the given
      // projection,
      // the lock would be broken
      assertThat(locked.specs()).hasSize(2).containsExactlyInAnyOrder(spec1, spec2);
    }

    @Test
    void withLockOnManagedProjection() {
      // INIT
      SimpleProjection managedProjection = new SimpleProjection();

      when(ehFactory.create(managedProjection)).thenReturn(projector);

      when(projector.createFactSpecs()).thenReturn(specs);

      // RUN
      Locked<SimpleProjection> locked = underTest.withLockOn(managedProjection);

      // ASSERT
      verify(ehFactory).create(managedProjection);

      verify(projector).createFactSpecs();

      assertThat(locked.factus()).isEqualTo(underTest);

      assertThat(locked.fc()).isEqualTo(fc);

      assertThat(locked.projectionOrNull()).isEqualTo(managedProjection);

      // this is important; if they are not the specs for the given
      // projection,
      // the lock would be broken
      assertThat(locked.specs()).isEqualTo(projector.createFactSpecs());
    }

    @Test
    void withLockOnAggregateClass_aggregateExists() {
      // INIT
      UUID aggId = randomUUID();
      when(ehFactory.create(any(PersonAggregate.class))).thenReturn(projector);

      when(projector.createFactSpecs()).thenReturn(specs);

      when(fc.subscribe(any(), any()))
          .thenAnswer(
              inv -> {
                // There must be one fact referencing this aggregate to make sure the
                // fact position is set, so that we do not get an empty optional when doing find.
                AbstractFactObserver fo = inv.getArgument(1, AbstractFactObserver.class);

                Fact f = new TestFact()
                        .ns("test")
                        .type("SomethingHappenedToPersonAggregate")
                        .aggId(aggId);
                // apply fact and with that, set position
                fo.onNext(Collections.singletonList(f));

                return mock(Subscription.class);
              });

      // RUN
      Locked<PersonAggregate> locked = underTest.withLockOn(PersonAggregate.class, aggId);

      // ASSERT
      verify(ehFactory, atLeast(1)).create(any());

      verify(projector, atLeast(1)).createFactSpecs();

      assertThat(locked.factus()).isEqualTo(underTest);

      assertThat(locked.fc()).isEqualTo(fc);

      // this is important; if they are not the specs for the given
      // projection, the lock would be broken
      assertThat(locked.specs()).isEqualTo(projector.createFactSpecs());
    }

    @Test
    void withLockOnAggregateClass_aggregateDoesNotExist() {
      // INIT
      when(ehFactory.create(any(PersonAggregate.class))).thenReturn(projector);

      when(projector.createFactSpecs()).thenReturn(specs);

      when(fc.subscribe(any(), any())).thenReturn(mock(Subscription.class));

      // RUN
      UUID aggId = fromString("40aaf918-c678-44a4-9962-ac6823a40ea5");
      assertThatThrownBy(() -> underTest.withLockOn(PersonAggregate.class, aggId))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage(
              "Aggregate PersonAggregate with id 40aaf918-c678-44a4-9962-ac6823a40ea5 does not exist.");
    }

    @Test
    void withLockOnSnapshotProjection() {
      // INIT
      when(ehFactory.create(any(ConcatCodesProjection.class))).thenReturn(projector);

      when(projector.createFactSpecs()).thenReturn(specs);

      when(fc.subscribe(any(), any())).thenReturn(mock(Subscription.class));

      // RUN
      Locked<ConcatCodesProjection> locked = underTest.withLockOn(ConcatCodesProjection.class);

      // ASSERT
      verify(ehFactory, atLeast(1)).create(any());

      verify(projector, atLeast(1)).createFactSpecs();

      assertThat(locked.factus()).isEqualTo(underTest);

      assertThat(locked.fc()).isEqualTo(fc);

      // this is important; if they are not the specs for the given
      // projection,
      // the lock would be broken
      assertThat(locked.specs()).isEqualTo(projector.createFactSpecs());
    }
  }

  @Mock private SnapshotSerializer snapshotSerializer;

  @Mock private Projector projector;

  @Captor ArgumentCaptor<List> factCaptor;

  @SuppressWarnings({"unchecked", "resource"})
  @Nested
  class WhenFetching {

    @Test
    void testSafeguard() {
      assertThatThrownBy(() -> underTest.fetch(SimpleAggregate.class))
          .isInstanceOf(IllegalArgumentException.class);
    }

    class SimpleAggregate extends Aggregate {}

    @Test
    void fetchWithNoEvents() {
      // INIT
      when(projectionSnapshotRepository.findLatest(ConcatCodesProjection.class))
          .thenReturn(Optional.empty());

      when(ehFactory.create(any(ConcatCodesProjection.class))).thenReturn(projector);

      when(projector.createFactSpecs()).thenReturn(specs);

      when(fc.subscribe(any(), any())).thenReturn(mock(Subscription.class));

      // RUN
      ConcatCodesProjection concatCodes = underTest.fetch(ConcatCodesProjection.class);

      // ASSERT
      assertThat(concatCodes.codes()).isEmpty();
    }

    @Test
    void fetchWithNoEventsButReturnsSnapshot() {
      when(ehFactory.create(any(ConcatCodesProjection.class))).thenReturn(projector);
      when(projector.createFactSpecs()).thenReturn(specs);
      when(fc.subscribe(any(), any())).thenReturn(mock(Subscription.class));

      // RUN
      ConcatCodesProjection concatCodes = underTest.fetch(ConcatCodesProjection.class);

      // ASSERT
      assertThat(concatCodes.codes()).isNotNull();
    }

    @Captor ArgumentCaptor<ConcatCodesProjection> projectionCaptor;

    @Test
    void fetchWithEvents() {
      when(projectionSnapshotRepository.findLatest(ConcatCodesProjection.class))
          .thenReturn(Optional.empty());

      // capture projection for later...
      when(ehFactory.create(projectionCaptor.capture())).thenReturn(projector);

      // make sure when event projector is asked to apply events, to wire
      // them through
      doAnswer(
              inv -> {
                List<Fact> facts = factCaptor.getValue();
                facts.forEach(
                    f -> {
                      if (f.jsonPayload().contains("abc")) {
                        projectionCaptor.getValue().apply(new SimpleEventObject("abc"));
                      } else {
                        projectionCaptor.getValue().apply(new SimpleEventObject("def"));
                      }
                    });
                return null;
              })
          .when(projector)
          .apply(factCaptor.capture());

      when(projector.createFactSpecs()).thenReturn(specs);

      when(fc.subscribe(any(), factObserverCaptor.capture()))
          .thenAnswer(
              inv -> {
                FactObserver factObserver = factObserverCaptor.getValue();

                // apply some new facts
                factObserver.onNext(toFact(new SimpleEventObject("abc")));
                factObserver.onNext(toFact(new SimpleEventObject("def")));
                factObserver.flush();
                return mock(Subscription.class);
              });

      // RUN
      ConcatCodesProjection concatCodes = underTest.fetch(ConcatCodesProjection.class);

      // ASSERT
      assertThat(concatCodes.codes()).isEqualTo("abcdef");
    }

    @Test
    void fetchRestoresAndCallsOnRestoreSnapshot() {
      // INIT
      mockSnapFactory();

      projectionSnapshotRepository =
          new SnapshotRepository(snapshotCache, snapFactory, factusMetrics);
      underTest =
          new FactusImpl(
              fc,
              ehFactory,
              eventConverter,
              aggregateSnapshotRepository,
              projectionSnapshotRepository,
              factusMetrics);

      ConcatCodesProjection dummyProjection = spy(new ConcatCodesProjection());
      dummyProjection.codes("abc");
      Assertions.assertThat(dummyProjection.codes).isEqualTo("abc");

      SnapshotSerializerId id = SnapshotSerializerId.of("willBeOverridden");
      byte[] serializedForm = new byte[] {1, 2, 3};
      UUID uuid = UUID.randomUUID();
      SnapshotData snapshotData = new SnapshotData(serializedForm, id, uuid);
      when(snapshotCache.find(any())).thenReturn(Optional.of(snapshotData));
      when(snapshotSerializer.deserialize(ConcatCodesProjection.class, serializedForm))
          .thenReturn(dummyProjection);
      when(ehFactory.create(projectionCaptor.capture())).thenReturn(projector);
      when(projector.createFactSpecs()).thenReturn(specs);
      when(fc.subscribe(any(), factObserverCaptor.capture())).thenReturn(mock(Subscription.class));

      // RUN
      ConcatCodesProjection concatCodes = underTest.fetch(ConcatCodesProjection.class);

      // ASSERT
      assertThat(concatCodes.codes()).isEqualTo("abc");
      verify(dummyProjection).onAfterRestore();
    }

    @Test
    void fetchRestoresAndAppliesEvents() {
      fetchRestoresAndCallsOnRestoreSnapshot();
      verify(fc)
          .subscribe(
              argThat(a -> a.specs().contains(specs.get(0)) && a.specs().size() == 1), any());
    }

    @Captor ArgumentCaptor<Runnable> runnableCaptor;

    @Test
    void eventHandlerCalled() {
      // INIT
      ConcatCodesProjection concatCodesProjection = spy(new ConcatCodesProjection());
      when(projectionSnapshotRepository.findLatest(ConcatCodesProjection.class))
          .thenReturn(Optional.of(ProjectionAndState.of(concatCodesProjection, randomUUID())));

      // capture projection for later...
      when(ehFactory.create(projectionCaptor.capture())).thenReturn(projector);

      when(projector.createFactSpecs()).thenReturn(specs);

      when(fc.subscribe(any(), factObserverCaptor.capture())).thenReturn(mock(Subscription.class));

      // RUN
      ConcatCodesProjection concatCodes = underTest.fetch(ConcatCodesProjection.class);

      // ASSERT
      FactObserver factObserver = factObserverCaptor.getValue();

      Fact mockedFact = Fact.builder().id(randomUUID()).buildWithoutPayload();

      // onNext(...)
      // now assume a new fact has been observed...
      factObserver.onNext(mockedFact);
      factObserver.flush();

      // ... and then it should be applied to event projector
      verify(projector).apply(Collections.singletonList(mockedFact));

      // onCatchup()
      // assume onCatchup got called on the fact observer...
      factObserver.onCatchup();

      // ... then make sure it got called on the subscribed projection
      verify(concatCodesProjection).onCatchup();

      // onComplete()
      // assume onComplete got called on the fact observer...
      factObserver.onComplete();

      // ... then make sure it got called on the subscribed projection
      verify(concatCodesProjection).onComplete();

      // onError(...)
      // assume onError got called on the fact observer...
      Exception exc = new Exception();
      factObserver.onError(exc);

      // ... then make sure it got called on the subscribed projection
      verify(concatCodesProjection).onError(exc);
    }
  }

  void mockSnapFactory() {
    when(snapFactory.selectSeralizerFor(any())).thenReturn(snapshotSerializer);
  }

  @SuppressWarnings("unchecked")
  @Nested
  class WhenSubscribing {

    @Captor ArgumentCaptor<FactObserver> factObserverArgumentCaptor;
    @Captor ArgumentCaptor<Duration> retryWaitTime;

    @Test
    void subscribe() {
      // INIT
      SubscribedProjection subscribedProjection = mock(SubscribedProjection.class);
      Projector<SubscribedProjection> eventApplier = mock(Projector.class);

      when(subscribedProjection.acquireWriteToken(any())).thenReturn(() -> {});

      when(ehFactory.create(subscribedProjection)).thenReturn(eventApplier);

      when(eventApplier.createFactSpecs())
          .thenReturn(Collections.singletonList(mock(FactSpec.class)));
      doAnswer(
              i -> {
                Fact argument = Iterables.getLast((List<Fact>) (i.getArgument(0)));
                subscribedProjection.factStreamPosition(FactStreamPosition.from(argument));
                return null;
              })
          .when(eventApplier)
          .apply(any(List.class));

      Subscription subscription = mock(Subscription.class);
      when(fc.subscribe(any(), any())).thenReturn(subscription);

      // RUN
      underTest.subscribeAndBlock(subscribedProjection);

      // ASSERT
      verify(subscribedProjection).acquireWriteToken(retryWaitTime.capture());
      assertThat(retryWaitTime.getValue()).isEqualTo(Duration.ofMinutes(5));

      verify(fc).subscribe(any(), factObserverArgumentCaptor.capture());

      FactObserver factObserver = factObserverArgumentCaptor.getValue();

      UUID factId = randomUUID();
      Fact mockedFact = Fact.builder().id(factId).serial(12L).buildWithoutPayload();

      // onNext(...)
      // now assume a new fact has been observed...
      factObserver.onNext(mockedFact);
      factObserver.flush();

      // ... and then it should be applied to event projector
      verify(eventApplier).apply(Lists.newArrayList(mockedFact));

      // ... and the fact stream position should be updated as well
      verify(subscribedProjection).factStreamPosition(FactStreamPosition.of(factId, 12));

      // onCatchup()
      // assume onCatchup got called on the fact observer...
      factObserver.onCatchup();

      // ... then make sure it got called on the subscribed projection
      verify(subscribedProjection).onCatchup();

      // onComplete()
      // assume onComplete got called on the fact observer...
      factObserver.onComplete();

      // ... then make sure it got called on the subscribed projection
      verify(subscribedProjection).onComplete();

      // onError(...)
      // assume onError got called on the fact observer...
      Exception exc = new Exception();
      factObserver.onError(exc);

      // ... then make sure it got called on the subscribed projection
      verify(subscribedProjection).onError(exc);
    }

    @Test
    void ignoresFastForwardIfBehindConsumedFact() {
      // INIT
      SubscribedProjection subscribedProjection = mock(SubscribedProjection.class);
      Projector<SubscribedProjection> eventApplier = mock(Projector.class);

      when(subscribedProjection.acquireWriteToken(any())).thenReturn(() -> {});

      when(ehFactory.create(subscribedProjection)).thenReturn(eventApplier);

      when(eventApplier.createFactSpecs())
          .thenReturn(Collections.singletonList(mock(FactSpec.class)));
      doAnswer(
              i -> {
                Fact argument = Iterables.getLast((List<Fact>) (i.getArgument(0)));
                subscribedProjection.factStreamPosition(FactStreamPosition.from(argument));
                return null;
              })
          .when(eventApplier)
          .apply(any(List.class));

      Subscription subscription = mock(Subscription.class);
      when(fc.subscribe(any(), any())).thenReturn(subscription);

      // RUN
      underTest.subscribeAndBlock(subscribedProjection);

      // ASSERT
      verify(subscribedProjection).acquireWriteToken(retryWaitTime.capture());
      assertThat(retryWaitTime.getValue()).isEqualTo(Duration.ofMinutes(5));

      verify(fc).subscribe(any(), factObserverArgumentCaptor.capture());

      FactObserver factObserver = factObserverArgumentCaptor.getValue();

      UUID factId = randomUUID();
      Fact mockedFact = Fact.builder().id(factId).serial(12L).buildWithoutPayload();
      FactStreamPosition ffwdPosition = FactStreamPosition.of(UUID.randomUUID(), 10L);

      // onNext(...)
      // now assume a new fact has been observed...
      factObserver.onNext(mockedFact);
      factObserver.flush();
      // ... and the fact stream position should be updated as well
      verify(subscribedProjection).factStreamPosition(FactStreamPosition.of(factId, 12));

      factObserver.onFastForward(ffwdPosition);

      verify(subscribedProjection, never()).factStreamPosition(ffwdPosition);
    }

    @Test
    void subscribeWithCustomRetryWaitTime() {
      // INIT
      SubscribedProjection subscribedProjection = mock(SubscribedProjection.class);
      Projector<SubscribedProjection> eventApplier = mock(Projector.class);

      when(subscribedProjection.acquireWriteToken(any())).thenReturn(() -> {});

      when(ehFactory.create(subscribedProjection)).thenReturn(eventApplier);

      when(eventApplier.createFactSpecs())
          .thenReturn(Collections.singletonList(mock(FactSpec.class)));
      doAnswer(
              i -> {
                Fact argument = Iterables.getLast((List<Fact>) (i.getArgument(0)));
                subscribedProjection.factStreamPosition(FactStreamPosition.from(argument));
                return null;
              })
          .when(eventApplier)
          .apply(any(List.class));

      Subscription subscription = mock(Subscription.class);
      when(fc.subscribe(any(), any())).thenReturn(subscription);

      // RUN
      underTest.subscribeAndBlock(subscribedProjection, Duration.ofMinutes(10));

      // ASSERT
      verify(subscribedProjection).acquireWriteToken(retryWaitTime.capture());
      assertThat(retryWaitTime.getValue()).isEqualTo(Duration.ofMinutes(10));

      verify(fc).subscribe(any(), factObserverArgumentCaptor.capture());

      FactObserver factObserver = factObserverArgumentCaptor.getValue();

      UUID factId = randomUUID();
      Fact mockedFact = Fact.builder().id(factId).serial(13L).buildWithoutPayload();

      // onNext(...)
      // now assume a new fact has been observed...
      factObserver.onNext(mockedFact);
      factObserver.flush();

      // ... and then it should be applied to event projector
      verify(eventApplier).apply(Lists.newArrayList(mockedFact));

      // ... and the fact stream position should be updated as well
      verify(subscribedProjection).factStreamPosition(FactStreamPosition.of(factId, 13L));

      // onCatchup()
      // assume onCatchup got called on the fact observer...
      factObserver.onCatchup();

      // ... then make sure it got called on the subscribed projection
      verify(subscribedProjection).onCatchup();

      // onComplete()
      // assume onComplete got called on the fact observer...
      factObserver.onComplete();

      // ... then make sure it got called on the subscribed projection
      verify(subscribedProjection).onComplete();

      // onError(...)
      // assume onError got called on the fact observer...
      Exception exc = new Exception();
      factObserver.onError(exc);

      // ... then make sure it got called on the subscribed projection
      verify(subscribedProjection).onError(exc);
    }

    @Test
    void closeSubscribed() throws Exception {
      // INIT
      SubscribedProjection subscribedProjection = mock(SubscribedProjection.class);
      Projector<SubscribedProjection> eventApplier = mock(Projector.class);

      when(subscribedProjection.acquireWriteToken(any())).thenReturn(() -> {});

      when(ehFactory.create(subscribedProjection)).thenReturn(eventApplier);

      when(eventApplier.createFactSpecs())
          .thenReturn(Collections.singletonList(mock(FactSpec.class)));

      Subscription subscription1 = mock(Subscription.class);
      Subscription subscription2 = mock(Subscription.class);
      when(fc.subscribe(any(), any())).thenReturn(subscription1, subscription2);

      doThrow(new Exception()).when(subscription1).close();

      doThrow(new Exception()).when(subscription2).close();

      // RUN
      underTest.subscribeAndBlock(subscribedProjection);
      underTest.subscribeAndBlock(subscribedProjection);

      // ASSERT
      // make sure when closing, managed objects are released
      underTest.close();

      // we do not know in which order they got closed, but both throw an
      // exception,
      // and still the other one needs to have been closed as well
      verify(subscription1).close();
      verify(subscription2).close();
    }

    @SneakyThrows
    @Test
    void throwsWhenClosedWhileWaitingForToken() {
      SubscribedProjection subscribedProjection = mock(SubscribedProjection.class);
      // waiting for token
      when(subscribedProjection.acquireWriteToken(any())).thenReturn(null);
      CompletableFuture<Void> future =
          CompletableFuture.runAsync(() -> underTest.subscribeAndBlock(subscribedProjection));

      verify(subscribedProjection, timeout(5000).atLeast(1)).acquireWriteToken(any());
      underTest.close();

      assertThatThrownBy(future::get).hasCauseExactlyInstanceOf(FactusClosedException.class);
    }
  }

  private void mockEventConverter() {
    when(eventConverter.toFact(any()))
        .thenAnswer(inv -> toFact(inv.getArgument(0, SimpleEventObject.class)));
  }

  @NonNull
  private Fact toFact(SimpleEventObject e) {
    return Fact.of(
        "{\"id\":  \"" + randomUUID() + "\", " + "\"ns\":  \"test\"}",
        "{\"val\": \"" + e.code() + "\"}");
  }

  @NonNull
  private Fact toFact(NameEvent e) {
    return Fact.of(
        "{\"id\":  \"" + randomUUID() + "\", " + "\"ns\":  \"test\"}",
        "{\"val\": \"" + e.name() + "\"}");
  }

  @Specification(ns = "test")
  @RequiredArgsConstructor
  static class SimpleEventObject implements EventObject {

    @Getter private final String code;

    @Override
    public Set<UUID> aggregateIds() {
      return Sets.newHashSet(UUID.fromString("31a364d5-ccde-494c-817d-fbf5c60a658b"));
    }
  }

  static class ConcatCodesProjection implements SnapshotProjection {

    @Getter private String codes = "";

    @Handler
    void apply(SimpleEventObject eventObject) {
      codes += eventObject.code;
    }

    public ConcatCodesProjection codes(String codes) {
      this.codes = codes;
      return this;
    }
  }

  @SuppressWarnings("unchecked")
  @Nested
  class WhenFinding {
    private final UUID AGGREGATE_ID = randomUUID();

    @Test
    void findWithNoEventsNoSnapshot() {
      // INIT
      when(ehFactory.create(any(PersonAggregate.class))).thenReturn(projector);

      when(projector.createFactSpecs()).thenReturn(specs);

      when(fc.subscribe(any(), any())).thenReturn(mock(Subscription.class));

      // RUN
      Optional<PersonAggregate> personAggregate =
          underTest.find(PersonAggregate.class, AGGREGATE_ID);

      // ASSERT
      assertThat(personAggregate).isEmpty();

      verify(fc).subscribe(any(), any());

      verify(projector, never()).apply(any(List.class));
    }

    @Test
    void findWithNoEventsButSnapshot() {
      // INIT
      PersonAggregate personAggregate = new PersonAggregate();
      when(aggregateSnapshotRepository.findLatest(PersonAggregate.class, AGGREGATE_ID))
          .thenReturn(Optional.of(ProjectionAndState.of(personAggregate, randomUUID())));

      when(ehFactory.create(any(PersonAggregate.class))).thenReturn(projector);

      when(projector.createFactSpecs()).thenReturn(specs);

      when(fc.subscribe(any(), any())).thenReturn(mock(Subscription.class));

      personAggregate.name("Fred");
      personAggregate.processed(1);

      // RUN
      Optional<PersonAggregate> personAggregateResult =
          underTest.find(PersonAggregate.class, AGGREGATE_ID);

      // ASSERT
      assertThat(personAggregateResult)
          .isPresent()
          .get()
          .extracting("name", "processed")
          .containsExactly("Fred", 1);
    }

    @Test
    void findWithEventsAndSnapshot() {
      // INIT
      PersonAggregate personAggregate = new PersonAggregate();

      when(aggregateSnapshotRepository.findLatest(PersonAggregate.class, AGGREGATE_ID))
          .thenReturn(Optional.of(ProjectionAndState.of(personAggregate, randomUUID())));

      when(ehFactory.create(any(PersonAggregate.class))).thenReturn(projector);

      when(projector.createFactSpecs()).thenReturn(specs);

      when(fc.subscribe(any(), any())).thenReturn(mock(Subscription.class));

      personAggregate.name("Fred");
      personAggregate.processed(1);

      // make sure when event projector is asked to apply events, to wire
      // them through
      doAnswer(
              inv -> {
                List<Fact> facts = factCaptor.getValue();
                facts.forEach(
                    f -> {
                      if (f.jsonPayload().contains("Barney")) {
                        personAggregate.process(new NameEvent("Barney"));
                      }
                    });
                return Void.TYPE;
              })
          .when(projector)
          .apply(factCaptor.capture());

      when(projector.createFactSpecs()).thenReturn(specs);

      when(fc.subscribe(any(), factObserverCaptor.capture()))
          .thenAnswer(
              inv -> {
                FactObserver factObserver = factObserverCaptor.getValue();

                // apply some new facts
                factObserver.onNext(toFact(new NameEvent("Barney")));
                factObserver.flush();
                return mock(Subscription.class);
              });

      // RUN
      Optional<PersonAggregate> personAggregateResult =
          underTest.find(PersonAggregate.class, AGGREGATE_ID);

      // ASSERT
      assertThat(personAggregateResult)
          .isPresent()
          .get()
          .extracting("name", "processed")
          .containsExactly("Barney", 2);
    }

    @Captor ArgumentCaptor<PersonAggregate> personAggregateCaptor;

    @Test
    void findWithEventsButNoSnapshot() {
      // INIT
      when(ehFactory.create(personAggregateCaptor.capture())).thenReturn(projector);

      when(projector.createFactSpecs()).thenReturn(specs);

      when(fc.subscribe(any(), any())).thenReturn(mock(Subscription.class));

      // make sure when event projector is asked to apply events, to wire
      // them through
      doAnswer(
              inv -> {
                List<Fact> facts = factCaptor.getValue();
                facts.forEach(
                    f -> {
                      if (f.jsonPayload().contains("Barney")) {
                        personAggregateCaptor.getValue().process(new NameEvent("Barney"));
                      }
                    });
                return Void.TYPE;
              })
          .when(projector)
          .apply(factCaptor.capture());

      when(projector.createFactSpecs()).thenReturn(specs);

      when(fc.subscribe(any(), factObserverCaptor.capture()))
          .thenAnswer(
              inv -> {
                FactObserver factObserver = factObserverCaptor.getValue();

                // apply some new facts
                factObserver.onNext(toFact(new NameEvent("Barney")));
                factObserver.flush();
                return mock(Subscription.class);
              });

      // RUN
      Optional<PersonAggregate> personAggregateResult =
          underTest.find(PersonAggregate.class, AGGREGATE_ID);

      // ASSERT
      assertThat(personAggregateResult)
          .isPresent()
          .get()
          .extracting("name", "processed")
          .containsExactly("Barney", 1);
    }
  }

  @Nested
  class IntervalSnapshotterTests {
    @SneakyThrows
    @Test
    void happyPath() {
      AtomicInteger calls = new AtomicInteger(0);
      Duration wait = Duration.ofSeconds(2);
      FactusImpl.IntervalSnapshotter<?> uut =
          new FactusImpl.IntervalSnapshotter<SnapshotProjection>(wait) {
            @Override
            void createSnapshot(SnapshotProjection projection, UUID state) {
              calls.incrementAndGet();
            }
          };

      uut.accept(null, randomUUID());
      uut.accept(null, randomUUID());
      uut.accept(null, randomUUID());
      uut.accept(null, randomUUID());

      assertThat(calls.get()).isZero();

      Thread.sleep(wait.toMillis() + 100);

      assertThat(calls.get()).isZero();
      uut.accept(null, randomUUID());
      uut.accept(null, randomUUID());

      assertThat(calls.get()).isOne();

      Thread.sleep(wait.toMillis() + 100);

      assertThat(calls.get()).isOne();
      uut.accept(null, randomUUID());
      uut.accept(null, randomUUID());

      assertThat(calls.get()).isEqualTo(2);
    }
  }

  @SuppressWarnings("resource")
  @Nested
  class WhenCatchingUp {
    @Test
    @SneakyThrows
    void returnsFactIdOfFastForward() {
      Projector pro = mock(Projector.class);
      Subscription sub = mock(Subscription.class);
      FactSpec spec1 = FactSpec.from(NameEvent.class);
      CompletableFuture<Void> cf = new CompletableFuture<>();

      Fact f = new TestFact();
      UUID id = f.id();
      FactStreamPosition pos = FactStreamPosition.of(id, 42L);

      when(ehFactory.create(any())).thenReturn(pro);
      when(fc.subscribe(any(SubscriptionRequest.class), any(FactObserver.class)))
          .thenAnswer(
              i -> {
                FactObserver fo = i.getArgument(1);
                fo.onFastForward(pos);
                return sub;
              });
      when(pro.createFactSpecs()).thenReturn(Lists.newArrayList(spec1));

      SomeSnapshotProjection p = new SomeSnapshotProjection();
      UUID ret = underTest.catchupProjection(p, UUID.randomUUID(), null);

      Assertions.assertThat(ret).isNotNull().isEqualTo(id);
    }

    @Test
    @SneakyThrows
    void ignoresFastForwardIfBeforeConsumedFact() {
      Projector pro = mock(Projector.class);
      Subscription sub = mock(Subscription.class);
      FactSpec spec1 = FactSpec.from(NameEvent.class);

      Fact f = new TestFact();
      UUID id = f.id();
      FactStreamPosition pos = FactStreamPosition.of(UUID.randomUUID(), 41L);

      when(ehFactory.create(any())).thenReturn(pro);
      when(fc.subscribe(any(SubscriptionRequest.class), any(FactObserver.class)))
          .thenAnswer(
              i -> {
                FactObserver fo = i.getArgument(1);
                fo.onNext(f);
                fo.onFastForward(pos);
                return sub;
              });
      when(pro.createFactSpecs()).thenReturn(Lists.newArrayList(spec1));

      SomeSnapshotProjection p = new SomeSnapshotProjection();
      UUID ret = underTest.catchupProjection(p, UUID.randomUUID(), null);

      Assertions.assertThat(ret).isNotNull().isEqualTo(id);
    }

    @Test
    @SneakyThrows
    void returnsFactIdOfLastFactApplied() {
      Projector pro = mock(Projector.class);
      Subscription sub = mock(Subscription.class);
      FactSpec spec1 = FactSpec.from(NameEvent.class);
      CompletableFuture<Void> cf = new CompletableFuture<>();

      Fact f = new TestFact();
      UUID id = f.id();

      when(ehFactory.create(any())).thenReturn(pro);
      when(fc.subscribe(any(SubscriptionRequest.class), any(FactObserver.class)))
          .thenAnswer(
              i -> {
                FactObserver fo = i.getArgument(1);
                fo.onNext(f);
                fo.onComplete();
                return sub;
              });
      when(pro.createFactSpecs()).thenReturn(Lists.newArrayList(spec1));

      SomeSnapshotProjection p = new SomeSnapshotProjection();
      UUID ret = underTest.catchupProjection(p, null, null);

      Assertions.assertThat(ret).isNotNull().isEqualTo(id);
    }

    @Test
    void returnsNoFactIdForwardTarget() {

      Projector pro = mock(Projector.class);
      Subscription sub = mock(Subscription.class);
      FactSpec spec1 = FactSpec.from(NameEvent.class);
      CompletableFuture<Void> cf = new CompletableFuture<>();

      UUID id = randomUUID();
      FactStreamPosition pos = FactStreamPosition.of(id, 42L);

      when(ehFactory.create(any())).thenReturn(pro);
      when(fc.subscribe(any(SubscriptionRequest.class), any(FactObserver.class)))
          .thenAnswer(
              i -> {
                FactObserver fo = i.getArgument(1);
                fo.onFastForward(pos);
                return sub;
              });
      when(pro.createFactSpecs()).thenReturn(Lists.newArrayList(spec1));

      SomeSnapshotProjection p = new SomeSnapshotProjection();
      UUID ret = underTest.catchupProjection(p, null, null);

      Assertions.assertThat(ret).isNull();
    }
  }

  @Test
  void testGetFactStore() {
    underTest.store();
    verify(fc).store();
  }
}

class SomeSnapshotProjection implements SnapshotProjection {
  @Handler
  void apply(NameEvent e) {}
}
