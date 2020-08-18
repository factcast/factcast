package org.factcast.factus;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.assertj.core.util.Lists;
import org.factcast.core.Fact;
import org.factcast.core.FactCast;
import org.factcast.core.event.EventConverter;
import org.factcast.factus.applier.EventApplierFactory;
import org.factcast.factus.batch.BatchAbortedException;
import org.factcast.factus.batch.PublishBatch;
import org.factcast.factus.event.EventObject;
import org.factcast.factus.event.Specification;
import org.factcast.factus.snapshot.AggregateSnapshotRepository;
import org.factcast.factus.snapshot.ProjectionSnapshotRepository;
import org.factcast.factus.snapshot.SnapshotSerializerSupplier;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
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
    void batch() {
    }

    @Nested
    class WhenPublishing {

        @Captor
        private ArgumentCaptor<Fact> factCaptor;

        @Captor
        private ArgumentCaptor<List<Fact>> factListCaptor;

        @Test
        public void publishFact() {
            // INIT
            Fact fact = toFact(new SimpleEventObject("a"));

            // RUN
            underTest.publish(fact);

            // ASSERT
            verify(fc)
                    .publish(fact);
        }

        @Test
        public void publishEventObject() {
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
        public void publishEventObjectWithFunction() {
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
        public void publishEventObjectList() {
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
    }

    @Nested
    class WhenBatching {

        @Captor
        private ArgumentCaptor<List<Fact>> factListCaptor;

        @Test
        public void simpleBatch() {

            // INIT
            mockEventConverter();

            // RUN
            try (PublishBatch batch = underTest.batch()) {
                batch
                        .add(new SimpleEventObject("a"))
                        .add(new SimpleEventObject("b"));
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
        public void batchAbortedWithErrorMessage() {
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
        public void batchAbortedWithException() {
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

    void mockEventConverter() {
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

    /*
     * @Nested class WhenPublishing {
     * 
     * @Mock private @NonNull EventObject e;
     * 
     * @Mock private @NonNull Function<Fact, T> resultFn;
     * 
     * @BeforeEach void setup() { } }
     * 
     * @Nested class WhenPublishing {
     * 
     * @Mock private EventObject eventObject;
     * 
     * @BeforeEach void setup() { } }
     * 
     * @Nested class WhenPublishing {
     * 
     * @Mock private @NonNull Fact f;
     * 
     * @BeforeEach void setup() { } }
     * 
     * @Nested class WhenPublishing {
     * 
     * @Mock private EventObject eventObject;
     * 
     * @Mock private @NonNull Function<List<Fact>, T> resultFn;
     * 
     * @BeforeEach void setup() { } }
     * 
     * @Nested class WhenUpdating {
     * 
     * @Mock private @NonNull P managedProjection;
     * 
     * @BeforeEach void setup() { } }
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