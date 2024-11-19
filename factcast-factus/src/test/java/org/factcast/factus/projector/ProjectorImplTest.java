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
package org.factcast.factus.projector;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.google.common.collect.Lists;
import java.lang.reflect.Method;
import java.util.*;
import javax.annotation.Nullable;
import lombok.Data;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.experimental.Delegate;
import org.assertj.core.api.Assertions;
import org.assertj.core.util.Maps;
import org.factcast.core.*;
import org.factcast.core.event.EventConverter;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.util.FactCastJson;
import org.factcast.factus.*;
import org.factcast.factus.event.DefaultEventSerializer;
import org.factcast.factus.event.EventObject;
import org.factcast.factus.event.EventSerializer;
import org.factcast.factus.event.Specification;
import org.factcast.factus.projection.FactStreamPositionAware;
import org.factcast.factus.projection.Projection;
import org.factcast.factus.projection.parameter.HandlerParameterContributors;
import org.factcast.factus.projection.tx.OpenTransactionAware;
import org.factcast.factus.projection.tx.TransactionAdapter;
import org.factcast.factus.projection.tx.TransactionBehavior;
import org.factcast.factus.projector.ProjectorImpl.ReflectionTools;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@SuppressWarnings({"deprecation", "java:S1186"})
class ProjectorImplTest {

  private final DefaultEventSerializer eventSerializer =
      new DefaultEventSerializer(FactCastJson.mapper());

  private final EventConverter eventConverter = new EventConverter(eventSerializer);

  @Nested
  class WhenApplying {

    @Test
    void applySimple() {
      // INIT
      UUID aggregateId = UUID.randomUUID();
      SimpleEvent event =
          new SimpleEvent(Maps.newHashMap("Some key", "Some value"), aggregateId, "abc");

      Fact fact = eventConverter.toFact(event);

      SimpleProjection projection = new SimpleProjection();

      ProjectorImpl<SimpleProjection> underTest = new ProjectorImpl<>(projection, eventSerializer);

      // RUN
      underTest.apply(Collections.singletonList(fact));

      // ASSERT
      assertThat(projection.recordedEvent()).isEqualTo(event);
    }

    @Test
    void applySimpleViaProjectorFactory() {
      // INIT
      UUID aggregateId = UUID.randomUUID();
      SimpleEvent event =
          new SimpleEvent(Maps.newHashMap("Some key", "Some value"), aggregateId, "abc");

      Fact fact = eventConverter.toFact(event);

      SimpleProjection projection = new SimpleProjection();

      DefaultProjectorFactory factory =
          new DefaultProjectorFactory(eventSerializer, new HandlerParameterContributors());
      Projector<SimpleProjection> underTest = factory.create(projection);

      // RUN
      underTest.apply(Collections.singletonList(fact));

      // ASSERT
      assertThat(projection.recordedEvent()).isEqualTo(event);
    }

    @Test
    void applyWithSubclass() {
      // INIT
      UUID aggregateId = UUID.randomUUID();
      UUID aggregateId2 = UUID.randomUUID();

      ComplexEvent event =
          new ComplexEvent(Maps.newHashMap("Some key", "Some value"), aggregateId, "abc");
      ComplexEvent2 event2 =
          new ComplexEvent2(Maps.newHashMap("Some key", "Some other value"), aggregateId2, "def");

      Fact fact = eventConverter.toFact(event);
      Fact fact2 = eventConverter.toFact(event2);

      ComplexProjection projection = new ComplexProjection();

      ProjectorImpl<ComplexProjection> underTest = new ProjectorImpl<>(projection, eventSerializer);

      // RUN
      underTest.apply(Lists.newArrayList(fact, fact2));

      // ASSERT
      assertThat(projection.recordedEvent()).isEqualTo(event);

      assertThat(projection.recordedEvent2()).isEqualTo(event2);
    }

    @Test
    void applyWithStaticSubclass() {
      SimpleProjectionWithStaticSubclass projection = new SimpleProjectionWithStaticSubclass();

      assertThatThrownBy(() -> new ProjectorImpl<>(projection, eventSerializer))
          .isInstanceOf(InvalidHandlerDefinition.class);
    }

    @Test
    void noSuchHandler() {
      // INIT
      UUID aggregateId = UUID.randomUUID();
      SimpleEvent event =
          new SimpleEvent(Maps.newHashMap("Some key", "Some value"), aggregateId, "abc");

      Fact fact = eventConverter.toFact(event);

      // complex projection does NOT have an event handler for
      // SimpleEvent!
      ComplexProjection projection = new ComplexProjection();

      ProjectorImpl<SimpleProjection> underTest = new ProjectorImpl<>(projection, eventSerializer);

      // RUN / ASSERT
      assertThatThrownBy(() -> underTest.apply(Collections.singletonList(fact)))
          .isInstanceOf(InvalidHandlerDefinition.class)
          .hasMessageStartingWith("Unexpected Fact coordinates");
    }

    @Test
    void applyWithHandlerFor() {
      // INIT
      UUID factId = UUID.randomUUID();
      UUID aggId = UUID.randomUUID();

      Fact fact = Fact.builder().ns("test").type("someType").id(factId).aggId(aggId).build("{}");

      ProjectionWithHandlerFor projection = new ProjectionWithHandlerFor();

      ProjectorImpl<SimpleProjection> underTest = new ProjectorImpl<>(projection, eventSerializer);

      assertThat(projection.factId()).isNull();
      assertThat(projection.fact()).isNull();
      assertThat(projection.factHeader()).isNull();

      // RUN
      underTest.apply(Collections.singletonList(fact));

      // ASSERT
      assertThat(projection.factId()).isEqualTo(factId);

      assertThat(projection.fact()).isEqualTo(fact);

      assertThat(projection.factHeader()).isEqualTo(fact.header());
    }
  }

  @Nested
  class WhenCreatingFactSpec {

    @Test
    void createSimple() {
      // INIT
      FactStreamPosition factStreamPosition =
          TestFactStreamPosition.fromString("9258562c-e6aa-4855-a765-3b1f49a113d5");

      ComplexProjection projection = new ComplexProjection();
      projection.factStreamPosition(factStreamPosition);

      ProjectorImpl<ComplexAggregate> underTest = new ProjectorImpl<>(projection, eventSerializer);

      // RUN
      List<FactSpec> factSpecs = underTest.createFactSpecs();

      // ASSERT
      assertThat(factSpecs)
          .hasSize(2)
          .flatExtracting(FactSpec::aggId, FactSpec::ns, FactSpec::version, FactSpec::type)
          .contains(
              // ComplexProjection has two handlers
              null, "test", 0, "ComplexEvent", null, "test", 0, "ComplexEvent2");
    }

    @Test
    void createFromAggregate() {
      // INIT
      UUID aggregateId = UUID.randomUUID();
      ComplexAggregate aggregate = new ComplexAggregate(aggregateId);

      ProjectorImpl<ComplexAggregate> underTest = new ProjectorImpl<>(aggregate, eventSerializer);

      // RUN
      List<FactSpec> factSpecs = underTest.createFactSpecs();

      // ASSERT
      assertThat(factSpecs)
          .hasSize(2)
          .flatExtracting(FactSpec::aggId, FactSpec::ns, FactSpec::version, FactSpec::type)
          .contains(
              // ComplexAggregate has two handlers
              aggregateId, "test", 0, "ComplexEvent", aggregateId, "test", 0, "ComplexEvent2");
    }

    @Test
    void createFromProjectionWithHandlerFor() {
      // INIT
      ProjectionWithHandlerFor projection = new ProjectionWithHandlerFor();

      ProjectorImpl<ProjectionWithHandlerFor> underTest =
          new ProjectorImpl<>(projection, eventSerializer);

      // RUN
      List<FactSpec> factSpecs = underTest.createFactSpecs();

      // ASSERT
      assertThat(factSpecs)
          .hasSize(1)
          .flatExtracting(FactSpec::aggId, FactSpec::ns, FactSpec::version, FactSpec::type)
          .contains(null, "test", 0, "someType");
    }

    @Test
    void invalidPostprocessReturnsNull() {
      // INIT
      ProjectorImpl<Projection> underTest =
          new ProjectorImpl<>(new PostProcessingProjection(null), eventSerializer);

      // RUN
      assertThatThrownBy(underTest::createFactSpecs)
          // ASSERT
          .isInstanceOf(InvalidHandlerDefinition.class)
          .hasMessageStartingWith("No FactSpecs discovered from");
    }

    @Test
    void invalidPostprocessReturnsEmptyList() {
      // INIT
      ProjectorImpl<Projection> underTest =
          new ProjectorImpl<>(
              new PostProcessingProjection(Collections.emptyList()), eventSerializer);

      // RUN
      assertThatThrownBy(underTest::createFactSpecs)
          // ASSERT
          .isInstanceOf(InvalidHandlerDefinition.class)
          .hasMessageStartingWith("No FactSpecs discovered from");
    }
  }

  @Nested
  class WhenConstructingWithDefectiveProjection {
    @Test
    void duplicateHandler() {
      // INIT / RUN
      assertThatThrownBy(
              () -> new ProjectorImpl<>(new DuplicateHandlerProjection(), eventSerializer))
          // ASSERT
          .isInstanceOf(InvalidHandlerDefinition.class)
          .hasMessageStartingWith("Duplicate Handler method found for spec");
    }

    @Test
    void duplicateArgumentHandler() {
      // INIT / RUN
      assertThatThrownBy(
              () -> new ProjectorImpl<>(new DuplicateArgumentProjection(), eventSerializer))
          // ASSERT
          .isInstanceOf(InvalidHandlerDefinition.class)
          .hasMessageStartingWith("Multiple EventPojo Parameters");
    }

    @Test
    void handlerWithoutParamters() {
      // INIT / RUN
      assertThatThrownBy(() -> new ProjectorImpl<>(new HandlerWithoutParameters(), eventSerializer))
          // ASSERT
          .isInstanceOf(InvalidHandlerDefinition.class)
          .hasMessageStartingWith("Handler methods must have at least one parameter");
    }

    @Test
    void handlerWithoutEventObjectParameters() {
      // INIT / RUN
      assertThatThrownBy(
              () -> new ProjectorImpl<>(new HandlerWithoutEventProjection(), eventSerializer))
          // ASSERT
          .isInstanceOf(InvalidHandlerDefinition.class)
          .hasMessageStartingWith("Cannot introspect FactSpec from");
    }

    @Test
    void nonVoidEventHandler() {
      // INIT / RUN
      assertThatThrownBy(() -> new ProjectorImpl<>(new NonVoidEventHandler(), eventSerializer))
          // ASSERT
          .isInstanceOf(InvalidHandlerDefinition.class)
          .hasMessageStartingWith("Handler methods must return void");
    }

    @Test
    void unknownHandlerParamType() {
      // INIT / RUN
      assertThatThrownBy(
              () -> new ProjectorImpl<>(new HandlerWithUnknownParameterType(), eventSerializer))
          // ASSERT
          .isInstanceOf(InvalidHandlerDefinition.class);
    }

    @Test
    void noHandler() {
      // INIT / RUN
      assertThatThrownBy(() -> new ProjectorImpl<>(new NoHandlerProjection(), eventSerializer))
          // ASSERT
          .isInstanceOf(InvalidHandlerDefinition.class)
          .hasMessageStartingWith("No handler methods discovered on");
    }

    @Test
    void handlerWithUnspecificVersion() {
      ProjectorImpl<Projection> p =
          new ProjectorImpl<>(new HandlerWithoutVersionProjection(), eventSerializer);

      assertThatNoException()
          .isThrownBy(
              () -> {
                Fact f1 = Fact.builder().ns("ns").type("type").version(1).buildWithoutPayload();
                Fact f2 = Fact.builder().ns("ns").type("type").version(2).buildWithoutPayload();

                p.apply(Lists.newArrayList(f1, f2));
              });
    }
  }

  @Nested
  class WhenResolvingTargets {

    @Test
    void resolveTargetFromStaticClass() {
      assertThat(ReflectionTools.resolveTargetObject(this, StaticClass.class))
          .isInstanceOf(StaticClass.class);
    }

    @Test
    void resolveTargetFromNonStaticClass() {
      assertThat(ReflectionTools.resolveTargetObject(ProjectorImplTest.this, NonStaticClass.class))
          .isInstanceOf(NonStaticClass.class);
    }
  }

  @Nested
  class WhenTestingForHandlerMethods {

    final ProjectorImpl<NonStaticClass> underTest =
        new ProjectorImpl<>(new NonStaticClass(), mock(EventSerializer.class));

    @Test
    void matchesHandlerMethods() throws NoSuchMethodException {
      Method realMethod = NonStaticClass.class.getDeclaredMethod("apply", SimpleEvent.class);
      assertThat(underTest.isEventHandlerMethod(realMethod)).isTrue();
    }

    @Test
    void ignoresMockitoMockProvidedMethods() throws NoSuchMethodException {
      Method realMethod =
          NonStaticClass$MockitoMock.class.getDeclaredMethod("apply", SimpleEvent.class);
      assertThat(underTest.isEventHandlerMethod(realMethod)).isFalse();
    }
  }

  @SuppressWarnings("InnerClassMayBeStatic")
  class NonStaticClass implements Projection {
    @Handler
    void apply(SimpleEvent e) {}
  }

  @SuppressWarnings("RedundantMethodOverride")
  class NonStaticClass$MockitoMock extends NonStaticClass {
    @Handler
    void apply(SimpleEvent e) {}
  }

  static class StaticClass {}

  // Working handlers

  @Value
  static class PostProcessingProjection implements Projection {

    List<FactSpec> factSpecs;

    @Override
    public @NonNull List<FactSpec> postprocess(@NonNull List<FactSpec> specsAsDiscovered) {
      return factSpecs;
    }

    @Handler
    void handle(SimpleEvent event) {
      // nothing
    }
  }

  @Data
  static class ProjectionWithHandlerFor implements Projection {

    private UUID factId;

    private Fact fact;

    private FactHeader factHeader;

    @HandlerFor(ns = "test", type = "someType")
    void handle(UUID factId, Fact fact, FactHeader factHeader) {
      this.factId = factId;
      this.fact = fact;
      this.factHeader = factHeader;
    }
  }

  // Faulty handlers

  @Value
  static class DuplicateHandlerProjection implements Projection {

    @Handler
    void handle(SimpleEvent event) {
      // nothing
    }

    @Handler
    void handleSame(SimpleEvent event) {
      // nothing
    }
  }

  @Value
  static class DuplicateArgumentProjection implements Projection {

    @Handler
    void handle(SimpleEvent event, SimpleEvent eventAgain) {
      // nothing
    }
  }

  @Value
  static class HandlerWithoutParameters implements Projection {

    @Handler
    void handle() {
      // nothing
    }
  }

  @Value
  static class HandlerWithoutEventProjection implements Projection {

    // UUID is valid parameter, but then we would have to
    // use @HandlerFor in order to get the fact specs
    @Handler
    void handle(UUID factId) {
      // nothing
    }
  }

  @Value
  static class NonVoidEventHandler implements Projection {

    @Handler
    int handle(SimpleEvent event) {
      // nothing
      return 0;
    }
  }

  @Value
  static class NoHandlerProjection implements Projection {}

  @Value
  static class HandlerWithUnknownParameterType implements Projection {

    @Handler
    void handle(Object someObject) {
      // nothing
    }
  }

  static class HandlerWithoutVersionProjection implements Projection {
    @HandlerFor(ns = "ns", type = "type")
    void applyFactWithoutSpecificVersion(Fact f) {}
  }

  public static class HandlerMethodsWithAdditionalFilters {
    @HandlerFor(ns = "ns", type = "type")
    @FilterByMeta(key = "foo", value = "bar")
    public void applyWithOneMeta(Fact f) {}

    @HandlerFor(ns = "ns", type = "type")
    @FilterByMeta(key = "foo", value = "bar")
    @FilterByMeta(key = "bar", value = "baz")
    public void applyWithMultiMeta(Fact f) {}

    @HandlerFor(ns = "ns", type = "type")
    @FilterByMetaExists("foo")
    public void applyWithOneMetaExists(Fact f) {}

    @HandlerFor(ns = "ns", type = "type")
    @FilterByMetaExists("foo")
    @FilterByMetaExists("bar")
    public void applyWithMultiMetaExists(Fact f) {}

    @HandlerFor(ns = "ns", type = "type")
    @FilterByMetaDoesNotExist("foo")
    public void applyWithOneMetaDoesNotExist(Fact f) {}

    @HandlerFor(ns = "ns", type = "type")
    @FilterByMetaDoesNotExist("foo")
    @FilterByMetaDoesNotExist("bar")
    public void applyWithMultiMetaDoesNotExist(Fact f) {}

    @HandlerFor(ns = "ns", type = "type")
    @FilterByAggId("1010a955-04a2-417b-9904-f92f88fdb67d")
    public void applyWithAggId(Fact f) {}

    @HandlerFor(ns = "ns", type = "type")
    @FilterByScript("function myfilter(e){}")
    public void applyWithFilterScript(Fact f) {}
  }

  @SneakyThrows
  @Test
  void detectsSingleMeta() {
    FactSpec spec = FactSpec.ns("ns");
    Method m = HandlerMethodsWithAdditionalFilters.class.getMethod("applyWithOneMeta", Fact.class);
    ReflectionTools.addOptionalFilterInfo(m, spec);

    assertThat(spec.meta()).containsEntry("foo", "bar").hasSize(1);
  }

  @SneakyThrows
  @Test
  void detectsMultiMeta() {
    FactSpec spec = FactSpec.ns("ns");
    Method m =
        HandlerMethodsWithAdditionalFilters.class.getMethod("applyWithMultiMeta", Fact.class);
    ReflectionTools.addOptionalFilterInfo(m, spec);

    assertThat(spec.meta()).containsEntry("foo", "bar").containsEntry("bar", "baz").hasSize(2);
  }

  @SneakyThrows
  @Test
  void detectsAggId() {
    FactSpec spec = FactSpec.ns("ns");
    Method m = HandlerMethodsWithAdditionalFilters.class.getMethod("applyWithAggId", Fact.class);
    ProjectorImpl.ReflectionTools.addOptionalFilterInfo(m, spec);

    assertThat(spec.aggId()).isEqualTo(UUID.fromString("1010a955-04a2-417b-9904-f92f88fdb67d"));
  }

  @SneakyThrows
  @Test
  void detectsMetaExists() {
    FactSpec spec = FactSpec.ns("ns");
    Method m =
        HandlerMethodsWithAdditionalFilters.class.getMethod("applyWithOneMetaExists", Fact.class);
    ProjectorImpl.ReflectionTools.addOptionalFilterInfo(m, spec);

    Assertions.assertThat(spec.metaKeyExists()).hasSize(1).containsEntry("foo", Boolean.TRUE);
  }

  @SneakyThrows
  @Test
  void detectsMultipleMetaExists() {
    FactSpec spec = FactSpec.ns("ns");
    Method m =
        HandlerMethodsWithAdditionalFilters.class.getMethod("applyWithMultiMetaExists", Fact.class);
    ProjectorImpl.ReflectionTools.addOptionalFilterInfo(m, spec);
    Assertions.assertThat(spec.metaKeyExists())
        .hasSize(2)
        .containsEntry("foo", Boolean.TRUE)
        .containsEntry("bar", Boolean.TRUE);
  }

  @SneakyThrows
  @Test
  void detectsMetaDoesNotExist() {
    FactSpec spec = FactSpec.ns("ns");
    Method m =
        HandlerMethodsWithAdditionalFilters.class.getMethod(
            "applyWithOneMetaDoesNotExist", Fact.class);
    ProjectorImpl.ReflectionTools.addOptionalFilterInfo(m, spec);
    Assertions.assertThat(spec.metaKeyExists()).hasSize(1).containsEntry("foo", Boolean.FALSE);
  }

  @SneakyThrows
  @Test
  void detectsMultipleMetaDoesNotExist() {
    FactSpec spec = FactSpec.ns("ns");
    Method m =
        HandlerMethodsWithAdditionalFilters.class.getMethod(
            "applyWithMultiMetaDoesNotExist", Fact.class);
    ProjectorImpl.ReflectionTools.addOptionalFilterInfo(m, spec);

    Assertions.assertThat(spec.metaKeyExists())
        .hasSize(2)
        .containsEntry("foo", Boolean.FALSE)
        .containsEntry("bar", Boolean.FALSE);
  }

  @SneakyThrows
  @Test
  void detectsFilterScript() {
    FactSpec spec = FactSpec.ns("ns");
    Method m =
        HandlerMethodsWithAdditionalFilters.class.getMethod("applyWithFilterScript", Fact.class);
    ProjectorImpl.ReflectionTools.addOptionalFilterInfo(m, spec);

    assertThat(spec.filterScript())
        .isEqualTo(org.factcast.core.spec.FilterScript.js("function myfilter(e){}"));
  }

  @Test
  void retryBatchUntilfailingFact() {
    TransactionalProjection projection = new TransactionalProjection();
    ProjectorImpl<SimpleProjection> underTest =
        spy(new ProjectorImpl<>(projection, eventSerializer));

    TestFact f1 = new TestFact();
    TestFact f2 = new TestFact();
    TestFact f3 = new TestFact();
    TestFact f4 = new TestFact();
    TestFact f5 = new TestFact();
    TestFact f6 = new TestFact();
    TestFact f8 = new TestFact();
    TestFact failing = new TestFact();

    List<Fact> batch = Lists.newArrayList(f1, f2, f3, f4, f5, f6, failing, f8);

    underTest.retryApplicableIfTransactional(batch, failing);

    verify(underTest).apply(Lists.newArrayList(f1, f2, f3, f4, f5, f6));
  }

  @Test
  void retrySkippedIfFirstIsFailing() {
    TransactionalProjection projection = new TransactionalProjection();
    ProjectorImpl<SimpleProjection> underTest =
        spy(new ProjectorImpl<>(projection, eventSerializer));

    TestFact f1 = new TestFact();
    TestFact f2 = new TestFact();

    TestFact failing = new TestFact();

    List<Fact> batch = Lists.newArrayList(failing, f1, f2);

    underTest.retryApplicableIfTransactional(batch, failing);

    verify(underTest, never()).apply(any());
  }

  interface SomeTransactionInterface {}

  static class TransactionalProjection
      implements OpenTransactionAware, TransactionAdapter<SomeTransactionInterface>, Projection {
    @Delegate private final TransactionBehavior<SomeTransactionInterface> transactionalBehavior;

    TransactionalProjection() {
      transactionalBehavior = new TransactionBehavior<>(this);
    }

    @HandlerFor(ns = "default", type = "test")
    void apply(Fact f) {}

    @Nullable
    @Override
    public FactStreamPosition factStreamPosition() {
      return TestFactStreamPosition.random();
    }

    @Override
    public void factStreamPosition(@NonNull FactStreamPosition factStreamPosition) {}

    @Override
    public void transactionalFactStreamPosition(@NonNull FactStreamPosition factStreamPosition) {}

    @Override
    public @NonNull SomeTransactionInterface beginNewTransaction() {
      return new SomeTransactionInterface() {
        @Override
        public int hashCode() {
          return super.hashCode();
        }
      };
    }

    @Override
    public void rollback(@NonNull SomeTransactionInterface runningTransaction) {}

    @Override
    public void commit(@NonNull SomeTransactionInterface runningTransaction) {}
  }

  @Test
  void commitTx() {
    TransactionalProjection projection = spy(new TransactionalProjection());
    ProjectorImpl<SimpleProjection> underTest =
        spy(new ProjectorImpl<>(projection, eventSerializer));
    underTest.beginIfTransactional();
    underTest.commitIfTransactional();

    verify(projection).commit();
  }

  @Test
  void rollbackTx() {
    TransactionalProjection projection = spy(new TransactionalProjection());
    ProjectorImpl<SimpleProjection> underTest =
        spy(new ProjectorImpl<>(projection, eventSerializer));
    underTest.beginIfTransactional();
    underTest.rollbackIfTransactional();

    verify(projection).rollback();
  }

  @Test
  void beginTx() {
    TransactionalProjection projection = spy(new TransactionalProjection());
    ProjectorImpl<SimpleProjection> underTest =
        spy(new ProjectorImpl<>(projection, eventSerializer));
    underTest.beginIfTransactional();

    verify(projection).begin();
  }

  @Nested
  class WhenFindingTypeParameter {

    @Test
    void determinesTypeParameter() {
      TransactionalProjection projection = spy(new TransactionalProjection());
      Assertions.assertThat(ReflectionTools.getTypeParameter(projection))
          .isSameAs(SomeTransactionInterface.class);
    }
  }

  @Nested
  class WhenApplyingToFSPAwareNonTx {

    class FSAProjection implements FactStreamPositionAware, Projection {
      private FactStreamPosition p;

      @Nullable
      @Override
      public FactStreamPosition factStreamPosition() {
        return p;
      }

      @Override
      public void factStreamPosition(@NonNull FactStreamPosition factStreamPosition) {
        this.p = factStreamPosition;
      }

      @HandlerFor(ns = "test", type = "test")
      void apply(Fact f) {
        if (f.header().meta().containsKey("pleaseThrow"))
          throw new RuntimeException("you asked me to");
      }
    }

    @Test
    void hasFSPosSetToFactBeforeErrorOccured() {
      Fact f1 = Fact.builder().ns("test").type("test").build("");
      Fact f2 = Fact.builder().ns("test").type("test").build("");
      Fact f3 = Fact.builder().ns("test").type("test").meta("pleaseThrow", "").build("");
      Fact f4 = Fact.builder().ns("test").type("test").build("");

      ArrayList<Fact> facts = Lists.newArrayList(f1, f2, f3, f4);

      FSAProjection projection = new FSAProjection();
      ProjectorImpl<Projection> uut = new ProjectorImpl<>(projection, eventSerializer);
      assertThatThrownBy(
              () -> {
                uut.apply(facts);
              })
          .isInstanceOf(Exception.class);

      Assertions.assertThat(projection.factStreamPosition()).isEqualTo(FactStreamPosition.from(f2));
    }

    @Test
    void hasFSPosSetOnEveryApply() {
      Fact f1 = Fact.builder().ns("test").type("test").build("");
      Fact f2 = Fact.builder().ns("test").type("test").build("");
      Fact f3 = Fact.builder().ns("test").type("test").build("");
      Fact f4 = Fact.builder().ns("test").type("test").build("");

      ArrayList<Fact> facts = Lists.newArrayList(f1, f2, f3, f4);

      FSAProjection projection = spy(new FSAProjection());
      ProjectorImpl<Projection> uut = new ProjectorImpl<>(projection, eventSerializer);
      uut.apply(facts);

      verify(projection, times(4)).factStreamPosition(any());
    }
  }

  @Nested
  class WhenDiscoveringHandlers {
    @Specification(ns = "x")
    class E implements EventObject {
      @Override
      public Set<UUID> aggregateIds() {
        return new HashSet<>();
      }
    }

    class P implements Projection {
      @Handler
      void apply(E e) {}
    }

    @Test
    void doesNotCacheEventSerializer() {
      EventSerializer e1 = mock(EventSerializer.class);
      EventSerializer e2 = mock(EventSerializer.class);

      new ProjectorImpl<>(new P(), e1); // discover & cache
      new ProjectorImpl<>(new P(), e2).apply(Lists.newArrayList(Fact.buildFrom(new E()).build()));

      verify(e1, never()).deserialize(same(E.class), anyString());
      verify(e2).deserialize(same(E.class), anyString());
    }
  }
}
