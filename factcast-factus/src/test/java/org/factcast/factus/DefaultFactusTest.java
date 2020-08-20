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

import static java.util.UUID.randomUUID;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.assertj.core.util.Lists;
import org.factcast.core.Fact;
import org.factcast.core.FactCast;
import org.factcast.core.event.EventConverter;
import org.factcast.core.event.EventSerializer;
import org.factcast.core.snap.Snapshot;
import org.factcast.core.snap.SnapshotId;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.observer.FactObserver;
import org.factcast.factus.applier.DefaultEventApplier;
import org.factcast.factus.applier.EventApplier;
import org.factcast.factus.applier.EventApplierFactory;
import org.factcast.factus.batch.BatchAbortedException;
import org.factcast.factus.batch.PublishBatch;
import org.factcast.factus.event.EventObject;
import org.factcast.factus.event.Specification;
import org.factcast.factus.lock.InLockedOperation;
import org.factcast.factus.projection.ManagedProjection;
import org.factcast.factus.projection.SnapshotProjection;
import org.factcast.factus.projection.SubscribedProjection;
import org.factcast.factus.serializer.SnapshotSerializer;
import org.factcast.factus.snapshot.AggregateSnapshotRepository;
import org.factcast.factus.snapshot.ProjectionSnapshotRepository;
import org.factcast.factus.snapshot.SnapshotSerializerSupplier;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import com.google.common.collect.Sets;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@ExtendWith(MockitoExtension.class)
class DefaultFactusTest {

    @Mock
    private FactCast fc;

    @Mock
    private EventApplierFactory ehFactory;

    @Mock
    private EventConverter eventConverter;

    @Mock
    private AggregateSnapshotRepository aggregateSnapshotRepository;

    @Mock
    private ProjectionSnapshotRepository projectionSnapshotRepository;

    @Mock
    private SnapshotSerializerSupplier snapFactory;

    @Mock
    private AtomicBoolean closed;

    @Mock
    private AutoCloseable autoCloseable;

    @InjectMocks
    private DefaultFactus underTest;

    @Test
    void testToFact() {
        // INIT
        Fact mockedFact = mock(Fact.class);
        EventObject mockedEventObject = mock(EventObject.class);

        when(eventConverter.toFact(mockedEventObject))
                .thenReturn(mockedFact);

        // RUN
        Fact fact = underTest.toFact(mockedEventObject);

        // ASSERT
        assertThat(fact)
                .isEqualTo(mockedFact);

        verify(eventConverter)
                .toFact(mockedEventObject);
    }

    @Nested
    class WhenPublishing {

        @Captor
        private ArgumentCaptor<Fact> factCaptor;

        @Captor
        private ArgumentCaptor<List<Fact>> factListCaptor;

        @Test
        void publishFact() {
            // INIT
            Fact fact = toFact(new SimpleEventObject("a"));

            // RUN
            underTest.publish(fact);

            // ASSERT
            verify(fc)
                    .publish(fact);
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
            verify(fc)
                    .publish(factCaptor.capture());

            assertThatJson(factCaptor.getValue().jsonPayload())
                    .and(f -> f.node("val").isEqualTo("a"));
        }

        @Test
        void publishEventObjectWithFunction() {
            // INIT
            EventObject eventObject = new SimpleEventObject("a");

            mockEventConverter();

            // RUN
            String jsonPayload = underTest.publish(eventObject, Fact::jsonPayload);

            // ASSERT
            verify(fc)
                    .publish(factCaptor.capture());

            assertThatJson(factCaptor.getValue().jsonPayload())
                    .and(f -> f.node("val").isEqualTo("a"));

            assertThatJson(jsonPayload)
                    .and(f -> f.node("val").isEqualTo("a"));
        }

        @Test
        void publishEventObjectList() {
            // INIT
            List<EventObject> eventObjects = Lists.newArrayList(
                    new SimpleEventObject("a"),
                    new SimpleEventObject("b"));

            mockEventConverter();

            // RUN
            underTest.publish(eventObjects);

            // ASSERT
            verify(fc)
                    .publish(factListCaptor.capture());

            assertThat(factListCaptor.getValue())
                    .anySatisfy(fact -> assertThatJson(fact.jsonPayload())
                            .and(f -> f.node("val").isEqualTo("a")))
                    .anySatisfy(fact -> assertThatJson(fact.jsonPayload())
                            .and(f -> f.node("val").isEqualTo("b")));
        }

        @Test
        void publishEventObjectListWithFunction() {
            // INIT
            List<EventObject> eventObjects = Lists.newArrayList(
                    new SimpleEventObject("a"),
                    new SimpleEventObject("b"));

            mockEventConverter();

            // RUN
            List<String> jsonPayloads = underTest.publish(eventObjects,
                    list -> list.stream()
                            .map(Fact::jsonPayload)
                            .collect(Collectors.toList()));

            // ASSERT
            verify(fc)
                    .publish(factListCaptor.capture());

            assertThat(factListCaptor.getValue())
                    .anySatisfy(fact -> assertThatJson(fact.jsonPayload())
                            .and(f -> f.node("val").isEqualTo("a")))
                    .anySatisfy(fact -> assertThatJson(fact.jsonPayload())
                            .and(f -> f.node("val").isEqualTo("b")));

            assertThat(jsonPayloads)
                    .anySatisfy(fact -> assertThatJson(fact)
                            .and(f -> f.node("val").isEqualTo("a")))
                    .anySatisfy(fact -> assertThatJson(fact)
                            .and(f -> f.node("val").isEqualTo("b")));
        }
    }

    @Nested
    class WhenBatching {

        @Captor
        private ArgumentCaptor<List<Fact>> factListCaptor;

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
            verify(fc)
                    .publish(factListCaptor.capture());

            assertThat(factListCaptor.getValue())
                    .anySatisfy(fact -> assertThatJson(fact.jsonPayload())
                            .and(f -> f.node("val").isEqualTo("a")))
                    .anySatisfy(fact -> assertThatJson(fact.jsonPayload())
                            .and(f -> f.node("val").isEqualTo("b")));
        }

        @Test
        void batchAbortedWithErrorMessage() {
            assertThatThrownBy(() -> {
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
            assertThatThrownBy(() -> {
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
            EventApplier<ManagedProjection> ea = Mockito.spy(new DefaultEventApplier<>(mock(
                    EventSerializer.class), m));
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
    class WhenFetching {

        @Mock
        private SnapshotSerializer snapshotSerializer;

        @Mock
        private EventApplier eventApplier;

        @Test
        @SuppressWarnings("unchecked")
        void fetchWithNoEvents() {
            // INIT
            when(projectionSnapshotRepository.findLatest(ConcatCodesProjection.class))
                    .thenReturn(Optional.empty());

            when(ehFactory.create(any(ConcatCodesProjection.class)))
                    .thenReturn(eventApplier);

            when(eventApplier.createFactSpecs())
                    .thenReturn(mock(List.class));

            when(fc.subscribe(any(), any()))
                    .thenReturn(mock(Subscription.class));

            // RUN
            ConcatCodesProjection concatCodes = underTest.fetch(ConcatCodesProjection.class);

            // ASSERT
            assertThat(concatCodes.codes())
                    .isEmpty();
        }

        @Test
        @SuppressWarnings("unchecked")
        void fetchWithNoEventsButSnapshot() {
            // INIT
            SnapshotId id = new SnapshotId("key", UUID.randomUUID());
            Snapshot snapshot = new Snapshot(id, randomUUID(), "foo".getBytes(), false);
            when(projectionSnapshotRepository.findLatest(ConcatCodesProjection.class))
                    .thenReturn(Optional.of(snapshot));

            when(ehFactory.create(any(ConcatCodesProjection.class)))
                    .thenReturn(eventApplier);

            when(eventApplier.createFactSpecs())
                    .thenReturn(mock(List.class));

            when(fc.subscribe(any(), any()))
                    .thenReturn(mock(Subscription.class));

            ConcatCodesProjection concatCodesProjection = new ConcatCodesProjection();
            concatCodesProjection.codes = "foo";

            when(snapshotSerializer.deserialize(ConcatCodesProjection.class, "foo".getBytes()))
                    .thenReturn(concatCodesProjection);

            // RUN
            ConcatCodesProjection concatCodes = underTest.fetch(ConcatCodesProjection.class);

            // ASSERT
            assertThat(concatCodes.codes())
                    .isEqualTo("foo");
        }

        @Captor
        ArgumentCaptor<FactObserver> factObserverCaptor;

        @Captor
        ArgumentCaptor<ConcatCodesProjection> projectionCaptor;

        @Captor
        ArgumentCaptor<Fact> factCaptor;

        @Test
        void fetchWithEvents() {
            // INIT
            when(projectionSnapshotRepository.findLatest(ConcatCodesProjection.class))
                    .thenReturn(Optional.empty());

            // capture projection for later...
            when(ehFactory.create(projectionCaptor.capture()))
                    .thenReturn(eventApplier);

            // make sure when event applier is asked to apply events, to wire
            // them through
            doAnswer(inv -> {
                if (factCaptor.getValue().jsonPayload().contains("abc")) {
                    projectionCaptor.getValue().apply(new SimpleEventObject("abc"));
                } else {
                    projectionCaptor.getValue().apply(new SimpleEventObject("def"));
                }

                return Void.TYPE;
            })
                    .when(eventApplier)
                    .apply(factCaptor.capture());

            when(eventApplier.createFactSpecs())
                    .thenReturn(mock(List.class));

            when(fc.subscribe(any(), factObserverCaptor.capture()))
                    .thenAnswer(inv -> {

                        FactObserver factObserver = factObserverCaptor.getValue();

                        // apply some new facts
                        factObserver.onNext(toFact(new SimpleEventObject("abc")));
                        factObserver.onNext(toFact(new SimpleEventObject("def")));

                        return mock(Subscription.class);
                    });

            // RUN
            ConcatCodesProjection concatCodes = underTest.fetch(ConcatCodesProjection.class);

            // ASSERT
            assertThat(concatCodes.codes())
                    .isEqualTo("abcdef");
        }

        @Test
        void fetchWithEventsAndSnapshot() {
            // INIT
            SnapshotId id = new SnapshotId("key", UUID.randomUUID());
            Snapshot snapshot = new Snapshot(id, randomUUID(), "foo".getBytes(), false);
            when(projectionSnapshotRepository.findLatest(ConcatCodesProjection.class))
                    .thenReturn(Optional.of(snapshot));

            // capture projection for later...
            when(ehFactory.create(projectionCaptor.capture()))
                    .thenReturn(eventApplier);

            // make sure when event applier is asked to apply events, to wire
            // them through
            doAnswer(inv -> {
                if (factCaptor.getValue().jsonPayload().contains("abc")) {
                    projectionCaptor.getValue().apply(new SimpleEventObject("abc"));
                } else {
                    projectionCaptor.getValue().apply(new SimpleEventObject("def"));
                }

                return Void.TYPE;
            })
                    .when(eventApplier)
                    .apply(factCaptor.capture());

            when(eventApplier.createFactSpecs())
                    .thenReturn(mock(List.class));

            when(fc.subscribe(any(), factObserverCaptor.capture()))
                    .thenAnswer(inv -> {

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
            assertThat(concatCodes.codes())
                    .isEqualTo("fooabcdef");
        }

        @BeforeEach
        void mockSnapFactory() {
            when(snapFactory.retrieveSerializer(ConcatCodesProjection.class))
                    .thenReturn(snapshotSerializer);
        }

    }

    @Nested
    class WhenSubscribing {

        @Captor
        ArgumentCaptor<FactObserver> factObserverArgumentCaptor;

        @Test
        @SuppressWarnings("unchecked")
        void subscribe() throws TimeoutException {
            // INIT
            SubscribedProjection subscribedProjection = mock(SubscribedProjection.class);
            EventApplier<SubscribedProjection> eventApplier = mock(EventApplier.class);

            when(subscribedProjection.acquireWriteToken(any()))
                    .thenReturn(mock(AutoCloseable.class));

            // make sure updates get executed
            doAnswer(inv -> {
                inv.getArgument(0, Runnable.class).run();
                return Void.TYPE;
            })
                    .when(subscribedProjection)
                    .executeUpdate(any());

            when(ehFactory.create(subscribedProjection))
                    .thenReturn(eventApplier);

            when(eventApplier.createFactSpecs())
                    .thenReturn(Arrays.asList(mock(FactSpec.class)));

            Subscription subscription = mock(Subscription.class);
            when(fc.subscribe(any(), any()))
                    .thenReturn(subscription);

            // RUN
            underTest.subscribe(subscribedProjection);

            // ASSERT
            verify(fc)
                    .subscribe(any(), factObserverArgumentCaptor.capture());

            FactObserver factObserver = factObserverArgumentCaptor.getValue();

            Fact mockedFact = mock(Fact.class);

            UUID factId = UUID.randomUUID();
            when(mockedFact.id())
                    .thenReturn(factId);

            // onNext(...)
            // now assume a new fact has been observed...
            factObserver.onNext(mockedFact);

            // ... then make sure executeUpdate got called...
            verify(subscribedProjection)
                    .executeUpdate(any());

            // ... and then it should be applied to event applier
            verify(eventApplier)
                    .apply(mockedFact);

            // ... and the state should be updated as well
            verify(subscribedProjection)
                    .state(factId);

            // onCatchup()
            // assume onCatchup got called on the fact observer...
            factObserver.onCatchup();

            // ... then make sure it got called on the subscribed projection
            verify(subscribedProjection)
                    .onCatchup();

            // onComplete()
            // assume onComplete got called on the fact observer...
            factObserver.onComplete();

            // ... then make sure it got called on the subscribed projection
            verify(subscribedProjection)
                    .onComplete();

            // onError(...)
            // assume onError got called on the fact observer...
            Exception exc = new Exception();
            factObserver.onError(exc);

            // ... then make sure it got called on the subscribed projection
            verify(subscribedProjection)
                    .onError(exc);

        }

    }

    private void mockEventConverter() {
        when(eventConverter.toFact(any()))
                .thenAnswer(inv -> toFact(inv.getArgument(0, SimpleEventObject.class)));
    }

    @NotNull
    private Fact toFact(SimpleEventObject e) {
        return Fact.of("{\"id\":  \"" + UUID.randomUUID() + "\", " +
                "\"ns\":  \"test\"}",
                "{\"val\": \"" + e.code() + "\"}");
    }

    @Specification(ns = "test")
    @RequiredArgsConstructor
    static class SimpleEventObject implements EventObject {

        @Getter
        private final String code;

        @Override
        public Set<UUID> aggregateIds() {
            return Sets.newHashSet(UUID.fromString("31a364d5-ccde-494c-817d-fbf5c60a658b"));
        }
    }

    static class ConcatCodesProjection implements SnapshotProjection {

        @Getter
        private String codes = "";

        @Handler
        void apply(SimpleEventObject eventObject) {
            codes += eventObject.code;
        }
    }

    /*
     *
     * @Nested class WhenSubscribing {
     *
     * @Mock private @NonNull P subscribedProjection;
     *
     * @BeforeEach void setup() { } }
     *
     * @Nested class WhenFetching {
     *
     * @Mock private Class<P> projectionClass;
     *
     * @BeforeEach void setup() { } }
     *
     * @Nested class WhenFinding { private final UUID AGGREGATE_ID =
     * UUID.randomUUID();
     *
     * @Mock private Class<A> aggregateClass;
     *
     * @BeforeEach void setup() { } }
     *
     * @Nested class WhenInitialing { private final UUID AGGREGATE_ID =
     * UUID.randomUUID();
     *
     * @Mock private Class<A> aggregateClass;
     *
     * @BeforeEach void setup() { } }
     *
     * @Nested class WhenClosing {
     *
     * @BeforeEach void setup() { } }
     *
     * @Nested class WhenToingFact {
     *
     * @Mock private @NonNull EventObject e;
     *
     * @BeforeEach void setup() { } }
     *
     * @Nested class WhenWithingLockOn {
     *
     * @Mock private M managedProjection;
     *
     * @BeforeEach void setup() { } }
     */

}
