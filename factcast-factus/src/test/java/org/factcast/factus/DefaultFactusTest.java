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

import static java.util.UUID.*;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.google.common.collect.Sets;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import lombok.*;
import org.assertj.core.util.Lists;
import org.factcast.core.Fact;
import org.factcast.core.FactCast;
import org.factcast.core.event.EventConverter;
import org.factcast.core.snap.Snapshot;
import org.factcast.core.snap.SnapshotId;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.observer.FactObserver;
import org.factcast.factus.DefaultFactus.IntervalSnapshotter;
import org.factcast.factus.batch.BatchAbortedException;
import org.factcast.factus.batch.PublishBatch;
import org.factcast.factus.event.EventObject;
import org.factcast.factus.event.EventSerializer;
import org.factcast.factus.event.Specification;
import org.factcast.factus.lock.InLockedOperation;
import org.factcast.factus.lock.Locked;
import org.factcast.factus.metrics.FactusMetrics;
import org.factcast.factus.metrics.FactusMetricsImpl;
import org.factcast.factus.projection.Aggregate;
import org.factcast.factus.projection.ManagedProjection;
import org.factcast.factus.projection.SnapshotProjection;
import org.factcast.factus.projection.SubscribedProjection;
import org.factcast.factus.projector.DefaultProjector;
import org.factcast.factus.projector.Projector;
import org.factcast.factus.projector.ProjectorFactory;
import org.factcast.factus.serializer.SnapshotSerializer;
import org.factcast.factus.snapshot.AggregateSnapshotRepository;
import org.factcast.factus.snapshot.ProjectionSnapshotRepository;
import org.factcast.factus.snapshot.SnapshotSerializerSupplier;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultFactusTest {

  @Mock private FactCast fc;

  @Mock private ProjectorFactory ehFactory;

  @Mock private EventConverter eventConverter;

  @Mock private AggregateSnapshotRepository aggregateSnapshotRepository;

  @Mock private ProjectionSnapshotRepository projectionSnapshotRepository;

  @Mock private SnapshotSerializerSupplier snapFactory;

  @Mock private AtomicBoolean closed;

  @Mock private AutoCloseable autoCloseable;

  @Spy private final FactusMetrics factusMetrics = new FactusMetricsImpl(new SimpleMeterRegistry());

  @InjectMocks private DefaultFactus underTest;

  @Captor ArgumentCaptor<FactObserver> factObserverCaptor;

  @Mock List<Specification> specs;

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
      Projector<ManagedProjection> ea =
          Mockito.spy(new DefaultProjector<>(mock(EventSerializer.class), m));
      when(ehFactory.create(m)).thenReturn(ea);
      ArgumentCaptor<FactObserver> observer = ArgumentCaptor.forClass(FactObserver.class);

      Fact f1 = Fact.builder().ns("test").type(SimpleEvent.class.getSimpleName()).build("{}");
      Fact f2 = Fact.builder().ns("test").type(SimpleEvent.class.getSimpleName()).build("{}");

      when(fc.subscribe(any(), observer.capture()))
          .thenAnswer(
              inv -> {
                FactObserver obs = observer.getValue();
                obs.onNext(f1);
                obs.onNext(f2);

                return Mockito.mock(Subscription.class);
              });
      underTest.update(m);

      // make sure m.executeUpdate is used
      Mockito.verify(m, times(2)).executeUpdate(any());
      // make sure m.executeUpdate actually calls the updated passed so
      // that
      // the prepared update happens on the projection and updates its
      // state.
      Mockito.verify(ea, times(2)).apply(any(Fact.class));
      assertThat(m.state()).isEqualTo(f2.id());
    }
  }

  @Nested
  class WhenConditionallyPublishing {

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

      assertThat(locked.projection()).isEqualTo(managedProjection);

      // this is important; if they are not the specs for the given
      // projection,
      // the lock would be broken
      assertThat(locked.specs()).isEqualTo(projector.createFactSpecs());
    }

    @Test
    void withLockOnAggregateClass() {
      // INIT
      mockSnapFactory();

      when(ehFactory.create(any(PersonAggregate.class))).thenReturn(projector);

      when(projector.createFactSpecs()).thenReturn(specs);

      when(fc.subscribe(any(), any())).thenReturn(mock(Subscription.class));

      // RUN
      UUID aggId = randomUUID();
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
    void withLockOnSnapshotProjection() {
      // INIT
      mockSnapFactory();

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

  @Captor ArgumentCaptor<Fact> factCaptor;

  @Nested
  class WhenFetching {

    @Test
    void testSafeguard() {
      assertThatThrownBy(() -> underTest.fetch(SimpleAggregate.class))
          .isInstanceOf(IllegalArgumentException.class);
    }

    class SimpleAggregate extends Aggregate {}

    @Test
    @SuppressWarnings("unchecked")
    void fetchWithNoEvents() {
      // INIT
      mockSnapFactory();

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
    @SuppressWarnings("unchecked")
    void fetchWithNoEventsButSnapshot() {
      // INIT
      mockSnapFactory();

      SnapshotId id = new SnapshotId("key", UUID.randomUUID());
      Snapshot snapshot = new Snapshot(id, randomUUID(), "foo".getBytes(), false);
      when(projectionSnapshotRepository.findLatest(ConcatCodesProjection.class))
          .thenReturn(Optional.of(snapshot));

      when(ehFactory.create(any(ConcatCodesProjection.class))).thenReturn(projector);

      when(projector.createFactSpecs()).thenReturn(specs);

      when(fc.subscribe(any(), any())).thenReturn(mock(Subscription.class));

      ConcatCodesProjection concatCodesProjection = new ConcatCodesProjection();
      concatCodesProjection.codes = "foo";

      when(snapshotSerializer.deserialize(ConcatCodesProjection.class, "foo".getBytes()))
          .thenReturn(concatCodesProjection);

      // RUN
      ConcatCodesProjection concatCodes = underTest.fetch(ConcatCodesProjection.class);

      // ASSERT
      assertThat(concatCodes.codes()).isEqualTo("foo");
    }

    @Captor ArgumentCaptor<ConcatCodesProjection> projectionCaptor;

    @Test
    void fetchWithEvents() {
      // INIT
      mockSnapFactory();

      when(projectionSnapshotRepository.findLatest(ConcatCodesProjection.class))
          .thenReturn(Optional.empty());

      // capture projection for later...
      when(ehFactory.create(projectionCaptor.capture())).thenReturn(projector);

      // make sure when event projector is asked to apply events, to wire
      // them through
      doAnswer(
              inv -> {
                if (factCaptor.getValue().jsonPayload().contains("abc")) {
                  projectionCaptor.getValue().apply(new SimpleEventObject("abc"));
                } else {
                  projectionCaptor.getValue().apply(new SimpleEventObject("def"));
                }

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
                factObserver.onNext(toFact(new SimpleEventObject("abc")));
                factObserver.onNext(toFact(new SimpleEventObject("def")));

                return mock(Subscription.class);
              });

      // RUN
      ConcatCodesProjection concatCodes = underTest.fetch(ConcatCodesProjection.class);

      // ASSERT
      assertThat(concatCodes.codes()).isEqualTo("abcdef");
    }

    @Test
    void fetchWithEventsAndSnapshot() {
      // INIT
      mockSnapFactory();

      SnapshotId id = new SnapshotId("key", UUID.randomUUID());
      Snapshot snapshot = new Snapshot(id, randomUUID(), "foo".getBytes(), false);
      when(projectionSnapshotRepository.findLatest(ConcatCodesProjection.class))
          .thenReturn(Optional.of(snapshot));

      // capture projection for later...
      when(ehFactory.create(projectionCaptor.capture())).thenReturn(projector);

      // make sure when event projector is asked to apply events, to wire
      // them through
      doAnswer(
              inv -> {
                if (factCaptor.getValue().jsonPayload().contains("abc")) {
                  projectionCaptor.getValue().apply(new SimpleEventObject("abc"));
                } else {
                  projectionCaptor.getValue().apply(new SimpleEventObject("def"));
                }

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
                factObserver.onNext(toFact(new SimpleEventObject("abc")));
                factObserver.onNext(toFact(new SimpleEventObject("def")));

                return mock(Subscription.class);
              });

      // prepare deserialiser for existing snapshot
      ConcatCodesProjection concatCodesProjection = new ConcatCodesProjection();
      concatCodesProjection.codes = "foo";

      when(snapshotSerializer.deserialize(ConcatCodesProjection.class, "foo".getBytes()))
          .thenReturn(concatCodesProjection);

      // RUN
      ConcatCodesProjection concatCodes = underTest.fetch(ConcatCodesProjection.class);

      // ASSERT
      assertThat(concatCodes.codes()).isEqualTo("fooabcdef");

      verify(projectionSnapshotRepository).put(eq(concatCodes), any());
    }

    @Captor ArgumentCaptor<Runnable> runnableCaptor;

    @Test
    void eventHandlerCalled() {
      // INIT
      mockSnapFactory();

      SnapshotId id = new SnapshotId("key", UUID.randomUUID());
      Snapshot snapshot = new Snapshot(id, randomUUID(), "foo".getBytes(), false);
      when(projectionSnapshotRepository.findLatest(ConcatCodesProjection.class))
          .thenReturn(Optional.of(snapshot));

      // capture projection for later...
      when(ehFactory.create(projectionCaptor.capture())).thenReturn(projector);

      when(projector.createFactSpecs()).thenReturn(specs);

      when(fc.subscribe(any(), factObserverCaptor.capture())).thenReturn(mock(Subscription.class));

      // prepare deserialiser for existing snapshot
      ConcatCodesProjection concatCodesProjection = mock(ConcatCodesProjection.class);

      doAnswer(
              inv -> {
                runnableCaptor.getValue().run();
                return Void.TYPE;
              })
          .when(concatCodesProjection)
          .executeUpdate(runnableCaptor.capture());

      when(snapshotSerializer.deserialize(ConcatCodesProjection.class, "foo".getBytes()))
          .thenReturn(concatCodesProjection);

      // RUN
      ConcatCodesProjection concatCodes = underTest.fetch(ConcatCodesProjection.class);

      // ASSERT
      FactObserver factObserver = factObserverCaptor.getValue();

      Fact mockedFact = mock(Fact.class);

      UUID factId = UUID.randomUUID();
      when(mockedFact.id()).thenReturn(factId);

      // onNext(...)
      // now assume a new fact has been observed...
      factObserver.onNext(mockedFact);

      // ... then make sure executeUpdate got called...
      verify(concatCodesProjection).executeUpdate(any());

      // ... and then it should be applied to event projector
      verify(projector).apply(mockedFact);

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
    when(snapFactory.retrieveSerializer(any())).thenReturn(snapshotSerializer);
  }

  @Nested
  class WhenSubscribing {

    @Captor ArgumentCaptor<FactObserver> factObserverArgumentCaptor;

    @Test
    @SuppressWarnings("unchecked")
    void subscribe() throws Exception {
      // INIT
      SubscribedProjection subscribedProjection = mock(SubscribedProjection.class);
      Projector<SubscribedProjection> eventApplier = mock(Projector.class);

      when(subscribedProjection.acquireWriteToken(any())).thenReturn(() -> {});

      // make sure updates get executed
      doAnswer(
              inv -> {
                inv.getArgument(0, Runnable.class).run();
                return Void.TYPE;
              })
          .when(subscribedProjection)
          .executeUpdate(any());

      when(ehFactory.create(subscribedProjection)).thenReturn(eventApplier);

      when(eventApplier.createFactSpecs()).thenReturn(Arrays.asList(mock(FactSpec.class)));

      Subscription subscription = mock(Subscription.class);
      when(fc.subscribe(any(), any())).thenReturn(subscription);

      // RUN
      underTest.subscribeAndBlock(subscribedProjection);

      // ASSERT
      verify(fc).subscribe(any(), factObserverArgumentCaptor.capture());

      FactObserver factObserver = factObserverArgumentCaptor.getValue();

      Fact mockedFact = mock(Fact.class);

      UUID factId = UUID.randomUUID();
      when(mockedFact.id()).thenReturn(factId);

      // onNext(...)
      // now assume a new fact has been observed...
      factObserver.onNext(mockedFact);

      // ... then make sure executeUpdate got called...
      verify(subscribedProjection).executeUpdate(any());

      // ... and then it should be applied to event projector
      verify(eventApplier).apply(mockedFact);

      // ... and the state should be updated as well
      verify(subscribedProjection).state(factId);

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
    @SuppressWarnings("unchecked")
    void closeSubscribed() throws Exception {
      // INIT
      SubscribedProjection subscribedProjection = mock(SubscribedProjection.class);
      Projector<SubscribedProjection> eventApplier = mock(Projector.class);

      when(subscribedProjection.acquireWriteToken(any())).thenReturn(() -> {});

      when(ehFactory.create(subscribedProjection)).thenReturn(eventApplier);

      when(eventApplier.createFactSpecs()).thenReturn(Arrays.asList(mock(FactSpec.class)));

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
  }

  private void mockEventConverter() {
    when(eventConverter.toFact(any()))
        .thenAnswer(inv -> toFact(inv.getArgument(0, SimpleEventObject.class)));
  }

  @NonNull
  private Fact toFact(SimpleEventObject e) {
    return Fact.of(
        "{\"id\":  \"" + UUID.randomUUID() + "\", " + "\"ns\":  \"test\"}",
        "{\"val\": \"" + e.code() + "\"}");
  }

  @NonNull
  private Fact toFact(NameEvent e) {
    return Fact.of(
        "{\"id\":  \"" + UUID.randomUUID() + "\", " + "\"ns\":  \"test\"}",
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
  }

  @Nested
  class WhenFinding {
    private final UUID AGGREGATE_ID = UUID.randomUUID();

    @Test
    void findWithNoEventsNoSnapshot() {
      // INIT
      mockSnapFactory();

      when(ehFactory.create(any(PersonAggregate.class))).thenReturn(projector);

      when(projector.createFactSpecs()).thenReturn(specs);

      when(fc.subscribe(any(), any())).thenReturn(mock(Subscription.class));

      // RUN
      Optional<PersonAggregate> personAggregate =
          underTest.find(PersonAggregate.class, AGGREGATE_ID);

      // ASSERT
      assertThat(personAggregate).isEmpty();

      verify(fc).subscribe(any(), any());

      verify(projector, never()).apply(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void findWithNoEventsButSnapshot() {
      // INIT
      mockSnapFactory();

      SnapshotId id = new SnapshotId("key", UUID.randomUUID());
      Snapshot snapshot = new Snapshot(id, randomUUID(), "Fred".getBytes(), false);
      when(aggregateSnapshotRepository.findLatest(PersonAggregate.class, AGGREGATE_ID))
          .thenReturn(Optional.of(snapshot));

      when(ehFactory.create(any(PersonAggregate.class))).thenReturn(projector);

      when(projector.createFactSpecs()).thenReturn(specs);

      when(fc.subscribe(any(), any())).thenReturn(mock(Subscription.class));

      PersonAggregate personAggregate = new PersonAggregate();
      personAggregate.name("Fred");
      personAggregate.processed(1);

      when(snapshotSerializer.deserialize(PersonAggregate.class, "Fred".getBytes()))
          .thenReturn(personAggregate);

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
    @SuppressWarnings("unchecked")
    void findWithEventsAndSnapshot() {
      // INIT
      mockSnapFactory();

      SnapshotId id = new SnapshotId("key", UUID.randomUUID());
      Snapshot snapshot = new Snapshot(id, randomUUID(), "Fred".getBytes(), false);
      when(aggregateSnapshotRepository.findLatest(PersonAggregate.class, AGGREGATE_ID))
          .thenReturn(Optional.of(snapshot));

      when(ehFactory.create(any(PersonAggregate.class))).thenReturn(projector);

      when(projector.createFactSpecs()).thenReturn(specs);

      when(fc.subscribe(any(), any())).thenReturn(mock(Subscription.class));

      PersonAggregate personAggregate = new PersonAggregate();
      personAggregate.name("Fred");
      personAggregate.processed(1);

      when(snapshotSerializer.deserialize(PersonAggregate.class, "Fred".getBytes()))
          .thenReturn(personAggregate);

      // make sure when event projector is asked to apply events, to wire
      // them through
      doAnswer(
              inv -> {
                if (factCaptor.getValue().jsonPayload().contains("Barney")) {
                  personAggregate.process(new NameEvent("Barney"));
                }
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
    @SuppressWarnings("unchecked")
    void findWithEventsButNoSnapshot() {
      // INIT
      mockSnapFactory();

      when(ehFactory.create(personAggregateCaptor.capture())).thenReturn(projector);

      when(projector.createFactSpecs()).thenReturn(specs);

      when(fc.subscribe(any(), any())).thenReturn(mock(Subscription.class));

      // make sure when event projector is asked to apply events, to wire
      // them through
      doAnswer(
              inv -> {
                if (factCaptor.getValue().jsonPayload().contains("Barney")) {
                  personAggregateCaptor.getValue().process(new NameEvent("Barney"));
                }
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
      Duration wait = Duration.ofSeconds(3);
      val uut =
          new IntervalSnapshotter<SnapshotProjection>(wait) {
            @Override
            void createSnapshot(SnapshotProjection projection, UUID state) {
              calls.incrementAndGet();
            }
          };

      uut.accept(null, UUID.randomUUID());
      uut.accept(null, UUID.randomUUID());
      uut.accept(null, UUID.randomUUID());
      uut.accept(null, UUID.randomUUID());

      assertThat(calls.get()).isEqualTo(0);

      Thread.sleep(wait.toMillis() + 100);

      assertThat(calls.get()).isEqualTo(0);
      uut.accept(null, UUID.randomUUID());
      uut.accept(null, UUID.randomUUID());

      assertThat(calls.get()).isEqualTo(1);

      Thread.sleep(wait.toMillis() + 100);

      assertThat(calls.get()).isEqualTo(1);
      uut.accept(null, UUID.randomUUID());
      uut.accept(null, UUID.randomUUID());

      assertThat(calls.get()).isEqualTo(2);
    }
  }
}
