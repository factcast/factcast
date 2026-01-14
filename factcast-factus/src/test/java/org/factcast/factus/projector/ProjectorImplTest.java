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

import com.google.common.collect.*;
import jakarta.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.*;
import lombok.*;
import lombok.experimental.*;
import lombok.experimental.Delegate;
import org.assertj.core.api.Assertions;
import org.assertj.core.util.Maps;
import org.factcast.core.*;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.spec.FilterScript;
import org.factcast.core.util.FactCastJson;
import org.factcast.factus.*;
import org.factcast.factus.event.*;
import org.factcast.factus.event.EventObject;
import org.factcast.factus.projection.*;
import org.factcast.factus.projection.parameter.*;
import org.factcast.factus.projection.tx.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;
import org.springframework.aop.target.SingletonTargetSource;

@SuppressWarnings({"deprecation", "java:S1186"})
@ExtendWith(MockitoExtension.class)
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
    void applyWithWildcard() {
      ComplexProjectionWithCatchall projection = new ComplexProjectionWithCatchall();

      ProjectorImpl<ComplexProjectionWithCatchall> underTest =
          new ProjectorImpl<>(projection, eventSerializer);

      // RUN
      underTest.apply(
          Lists.newArrayList(
              Fact.builder()
                  .ns("alien")
                  .type("alsoAlien")
                  .version(1)
                  .build("{\"code\":\"poit\"}")));

      assertThat(projection.recordedEvent3().code()).isEqualTo("poit");
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
          .flatExtracting(FactSpec::aggIds, FactSpec::ns, FactSpec::version, FactSpec::type)
          .containsExactlyInAnyOrder(
              // ComplexProjection has two handlers
              Collections.emptySet(),
              "test",
              0,
              "ComplexEvent",
              Collections.emptySet(),
              "test",
              0,
              "ComplexEvent2");
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
      Set<UUID> expectedAggIds = Collections.singleton(aggregateId);
      assertThat(factSpecs)
          .hasSize(2)
          .flatExtracting(FactSpec::aggIds, FactSpec::ns, FactSpec::version, FactSpec::type)
          .contains(
              // ComplexAggregate has two handlers
              expectedAggIds,
              "test",
              0,
              "ComplexEvent",
              expectedAggIds,
              "test",
              0,
              "ComplexEvent2");
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
          .flatExtracting(FactSpec::aggIds, FactSpec::ns, FactSpec::version, FactSpec::type)
          .contains(Collections.emptySet(), "test", 0, "someType");
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
      assertThat(ReflectionUtils.resolveTargetObject(this, StaticClass.class))
          .isInstanceOf(StaticClass.class);
    }

    @Test
    void resolveTargetFromNonStaticClass() {
      assertThat(ReflectionUtils.resolveTargetObject(ProjectorImplTest.this, NonStaticClass.class))
          .isInstanceOf(NonStaticClass.class);
    }
  }

  @Nested
  class WhenTestingForHandlerMethods {

    final ProjectorImpl<NonStaticClass> underTest =
        new ProjectorImpl<>(new NonStaticClass(), eventSerializer);

    @Test
    void matchesHandlerMethods() throws NoSuchMethodException {
      Method realMethod = NonStaticClass.class.getDeclaredMethod("apply", SimpleEvent.class);
      assertThat(ReflectionUtils.isEventHandlerMethod(realMethod)).isTrue();
    }

    @Test
    void ignoresMockitoMockProvidedMethods() throws NoSuchMethodException {
      Method realMethod =
          NonStaticClass$MockitoMock.class.getDeclaredMethod("apply", SimpleEvent.class);
      assertThat(ReflectionUtils.isEventHandlerMethod(realMethod)).isFalse();
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
    @FilterByAggId({"1010a955-04a2-417b-9904-f92f88fdb67d", "1010a955-04a2-417b-9904-f92f88fdb67e"})
    public void applyWithMultipleAggIds(Fact f) {}

    @HandlerFor(ns = "ns", type = "type")
    @FilterByScript("function myfilter(e){}")
    public void applyWithFilterScript(Fact f) {}
  }

  @SneakyThrows
  @Test
  void detectsSingleMeta() {
    FactSpec spec = FactSpec.ns("ns");
    Method m = HandlerMethodsWithAdditionalFilters.class.getMethod("applyWithOneMeta", Fact.class);
    ReflectionUtils.addOptionalFilterInfo(m, spec);

    assertThat(spec.meta()).containsEntry("foo", "bar").hasSize(1);
  }

  @SneakyThrows
  @Test
  void detectsMultiMeta() {
    FactSpec spec = FactSpec.ns("ns");
    Method m =
        HandlerMethodsWithAdditionalFilters.class.getMethod("applyWithMultiMeta", Fact.class);
    ReflectionUtils.addOptionalFilterInfo(m, spec);

    assertThat(spec.meta()).containsEntry("foo", "bar").containsEntry("bar", "baz").hasSize(2);
  }

  @SneakyThrows
  @Test
  void detectsAggId() {
    FactSpec spec = FactSpec.ns("ns");
    Method m = HandlerMethodsWithAdditionalFilters.class.getMethod("applyWithAggId", Fact.class);
    ReflectionUtils.addOptionalFilterInfo(m, spec);

    assertThat(spec.aggIds()).containsOnly(UUID.fromString("1010a955-04a2-417b-9904-f92f88fdb67d"));
  }

  @SneakyThrows
  @Test
  void detectsMultipleAggIds() {
    FactSpec spec = FactSpec.ns("ns");
    Method m =
        HandlerMethodsWithAdditionalFilters.class.getMethod("applyWithMultipleAggIds", Fact.class);
    ReflectionUtils.addOptionalFilterInfo(m, spec);

    assertThat(spec.aggIds())
        .containsOnly(
            UUID.fromString("1010a955-04a2-417b-9904-f92f88fdb67d"),
            UUID.fromString("1010a955-04a2-417b-9904-f92f88fdb67e"));
  }

  @SneakyThrows
  @Test
  void detectsMetaExists() {
    FactSpec spec = FactSpec.ns("ns");
    Method m =
        HandlerMethodsWithAdditionalFilters.class.getMethod("applyWithOneMetaExists", Fact.class);
    ReflectionUtils.addOptionalFilterInfo(m, spec);

    assertThat(spec.metaKeyExists()).hasSize(1).containsEntry("foo", Boolean.TRUE);
  }

  @SneakyThrows
  @Test
  void detectsMultipleMetaExists() {
    FactSpec spec = FactSpec.ns("ns");
    Method m =
        HandlerMethodsWithAdditionalFilters.class.getMethod("applyWithMultiMetaExists", Fact.class);
    ReflectionUtils.addOptionalFilterInfo(m, spec);
    assertThat(spec.metaKeyExists())
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
    ReflectionUtils.addOptionalFilterInfo(m, spec);
    assertThat(spec.metaKeyExists()).hasSize(1).containsEntry("foo", Boolean.FALSE);
  }

  @SneakyThrows
  @Test
  void detectsMultipleMetaDoesNotExist() {
    FactSpec spec = FactSpec.ns("ns");
    Method m =
        HandlerMethodsWithAdditionalFilters.class.getMethod(
            "applyWithMultiMetaDoesNotExist", Fact.class);
    ReflectionUtils.addOptionalFilterInfo(m, spec);

    assertThat(spec.metaKeyExists())
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
    ReflectionUtils.addOptionalFilterInfo(m, spec);

    assertThat(spec.filterScript()).isEqualTo(FilterScript.js("function myfilter(e){}"));
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
      assertThat(ReflectionUtils.getTypeParameter(projection))
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
        if (f.header().meta().containsKey("pleaseThrow")) {
          throw new RuntimeException("you asked me to");
        }
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

      assertThat(projection.factStreamPosition()).isEqualTo(FactStreamPosition.from(f2));
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

  @Specification(ns = "ns1")
  static class E1 implements EventObject {
    @Override
    public Set<UUID> aggregateIds() {
      return new HashSet<>();
    }
  }

  @Specification(ns = "ns2")
  static class E2 implements EventObject {
    @Override
    public Set<UUID> aggregateIds() {
      return new HashSet<>();
    }
  }

  @Specification(ns = "ns2")
  static class Unrelated implements EventObject {
    @Override
    public Set<UUID> aggregateIds() {
      return new HashSet<>();
    }
  }

  @OverrideNamespace(ns = "i-xyz", type = Unrelated.class)
  interface SomeProjectionInterface extends Projection {}

  @OverrideNamespace(ns = "d-xyz", type = Unrelated.class)
  @OverrideNamespace(ns = "d-ns2", type = E1.class)
  static class SomeProjectionSuperClass implements Projection {}

  @OverrideNamespace(ns = "s-xyz", type = Unrelated.class)
  @OverrideNamespace(ns = "s-targetForE1", type = E1.class)
  @OverrideNamespace(ns = "s-targetForE2", type = E2.class)
  static class SomeProjectionWithTypeAnnotation implements Projection {
    @Handler
    void apply(E1 e) {}
  }

  static class SomeProjectionWithTypeAnnotationOnParent extends SomeProjectionWithTypeAnnotation {
    @Handler
    void apply(E2 e) {}
  }

  static class SomeProjectionWithMethodLevelOverride implements Projection {
    @OverrideNamespace(ns = "m-targetForE2")
    @Handler
    void apply(E2 e) {}
  }

  static class SomeProjectionWithMethodLevelLegalTargetType implements Projection {
    @OverrideNamespace(ns = "m-targetForE2", type = E2.class)
    @Handler
    void apply(E2 e) {}
  }

  static class SomeProjectionWithMethodLevelIllegalTargetType implements Projection {
    @OverrideNamespace(ns = "blowup", type = E1.class)
    @Handler
    void apply(E2 e) {}
  }

  static class SomeProjectionWithOverrideOnInterface implements SomeProjectionInterface {
    @Handler
    void apply(E1 e) {}

    @Handler
    void apply(E2 e) {}
  }

  @OverrideNamespace(ns = "l3", type = E1.class)
  static class L3 implements Projection {
    @Handler
    void apply(E1 e) {}
  }

  @OverrideNamespace(ns = "l2", type = E2.class)
  static class L2 extends L3 {}

  @OverrideNamespace(ns = "l1", type = E1.class)
  static class L1 extends L2 {}

  @Nested
  class WhenOverriding {
    @Test
    void overridesNsFromMethodLevelAnnotationDiscover() {
      ProjectorImpl<Projection> uut =
          new ProjectorImpl<>(new SomeProjectionWithMethodLevelOverride(), eventSerializer);
      List<FactSpec> factSpecs = uut.createFactSpecs();
      assertThat(factSpecs).hasSize(1);
      assertThat(factSpecs.get(0).ns()).isEqualTo("m-targetForE2");
    }

    @Test
    void overridesNsFromMethodLevelAnnotationLegal() {
      ProjectorImpl<Projection> uut =
          new ProjectorImpl<>(new SomeProjectionWithMethodLevelLegalTargetType(), eventSerializer);
      List<FactSpec> factSpecs = uut.createFactSpecs();
      assertThat(factSpecs).hasSize(1);
      assertThat(factSpecs.get(0).ns()).isEqualTo("m-targetForE2");
    }

    @Test
    void overridesNsFromMethodLevelAnnotationIllegal() {
      SomeProjectionWithMethodLevelIllegalTargetType p =
          new SomeProjectionWithMethodLevelIllegalTargetType();
      assertThatThrownBy(
              () -> {
                new ProjectorImpl<>(p, eventSerializer);
              })
          .isInstanceOf(InvalidHandlerDefinition.class);
    }

    @Test
    void overridesNsFromTypeLevelAnnotation() {
      ProjectorImpl<Projection> uut =
          new ProjectorImpl<>(new SomeProjectionWithTypeAnnotation(), eventSerializer);
      List<FactSpec> factSpecs = uut.createFactSpecs();
      assertThat(factSpecs).hasSize(1);
      assertThat(factSpecs.get(0).ns()).isEqualTo("s-targetForE1");
    }

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    @Test
    void overridesNsFromTypeLevelAnnotationOnSuper() {
      ProjectorImpl<Projection> uut =
          new ProjectorImpl<>(new SomeProjectionWithTypeAnnotationOnParent(), eventSerializer);
      List<FactSpec> factSpecs = uut.createFactSpecs();
      Optional<FactSpec> e1 = factSpecs.stream().filter(fs -> "E1".equals(fs.type())).findFirst();
      Optional<FactSpec> e2 = factSpecs.stream().filter(fs -> "E2".equals(fs.type())).findFirst();
      assertThat(e1.get().ns()).isEqualTo("s-targetForE1");
      assertThat(e2.get().ns()).isEqualTo("s-targetForE2");
    }

    @Test
    void overridesNsFromTypeLevelAnnotationOnInterface() {
      SomeProjectionWithOverrideOnInterface p = new SomeProjectionWithOverrideOnInterface();
      assertThatThrownBy(
              () -> {
                new ProjectorImpl<>(p, eventSerializer);
              })
          .isInstanceOf(InvalidHandlerDefinition.class);
    }

    @Test
    void deserializesFromOverriddenNs() {
      Fact factWithChangedNs = Fact.buildFrom(new E2()).ns("m-targetForE2").build();

      SomeProjectionWithMethodLevelOverride p = spy(new SomeProjectionWithMethodLevelOverride());
      ProjectorImpl<Projection> uut = new ProjectorImpl<>(p, eventSerializer);
      uut.apply(Lists.newArrayList(factWithChangedNs));

      verify(p).apply(any(E2.class));
    }

    @Test
    void deepInspection() {
      ProjectorImpl<Projection> uut = new ProjectorImpl<>(new L1(), eventSerializer);
      assertThat(uut.createFactSpecs().get(0).ns()).isEqualTo("l1");
    }

    @Test
    void deepInspection2() {
      ProjectorImpl<Projection> uut = new ProjectorImpl<>(new L2(), eventSerializer);
      assertThat(uut.createFactSpecs().get(0).ns()).isEqualTo("l3");
    }

    @Test
    void deepInspection3() {
      ProjectorImpl<Projection> uut = new ProjectorImpl<>(new L3(), eventSerializer);
      assertThat(uut.createFactSpecs().get(0).ns()).isEqualTo("l3");
    }
  }

  @Nested
  class WhenUnwrapping {
    @Mock Advised a;

    @Mock Object b;

    @Test
    void throwsIfTargetIsNull() {

      try (MockedStatic<AopUtils> utilities = Mockito.mockStatic(AopUtils.class)) {
        utilities.when(() -> AopUtils.isAopProxy(any())).thenReturn(true);
        when(a.getTargetSource()).thenReturn(null);

        assertThatThrownBy(
                () -> {
                  ProjectorImpl.unwrapProxy(a);
                })
            .isInstanceOf(NullPointerException.class);
      }
    }

    @Test
    void breaksCircuit() {
      try (MockedStatic<AopUtils> utilities = Mockito.mockStatic(AopUtils.class)) {
        utilities.when(() -> AopUtils.isAopProxy(any())).thenReturn(true);
        when(a.getTargetSource()).thenReturn(new SingletonTargetSource(a));

        assertThatThrownBy(
                () -> {
                  ProjectorImpl.unwrapProxy(a);
                })
            .isInstanceOf(IllegalStateException.class);
      }
    }

    @Test
    void unwraps() {
      try (MockedStatic<AopUtils> utilities = Mockito.mockStatic(AopUtils.class)) {
        utilities.when(() -> AopUtils.isAopProxy(any())).thenReturn(true);
        when(a.getTargetSource()).thenReturn(new SingletonTargetSource(b));
        assertThat(ProjectorImpl.unwrapProxy(a)).isSameAs(b);
      }
    }

    @Test
    void leavesUnrelatedObjectsAlone() {
      try (MockedStatic<AopUtils> utilities = Mockito.mockStatic(AopUtils.class)) {
        utilities.when(() -> AopUtils.isAopProxy(any())).thenReturn(true);
        assertThat(ProjectorImpl.unwrapProxy(b)).isSameAs(b);
      }
    }
  }

  @Nested
  class WhenFindingEventObjectParamType {

    class SomeEvent implements EventObject {
      @Override
      public Set<UUID> aggregateIds() {
        return Collections.emptySet();
      }
    }

    class OtherEvent implements EventObject {
      @Override
      public Set<UUID> aggregateIds() {
        return Collections.emptySet();
      }
    }

    class SomeProjection implements Projection {
      @Handler
      void empty() {}

      @Handler
      void multi(SomeEvent event, OtherEvent bad) {}

      @Handler
      void none(Fact f) {}

      @Handler
      void apply(Fact f, SomeEvent event, UUID factID) {}
    }

    @SneakyThrows
    Method methodByName(String name) {
      return Arrays.stream(SomeProjection.class.getDeclaredMethods())
          .filter(m -> name.equals(m.getName()))
          .findFirst()
          .get();
    }

    @Test
    @SneakyThrows
    void failsOnEmptyParamList() {
      assertThatThrownBy(() -> ReflectionUtils.findEventObjectParameterType(methodByName("empty")))
          .isInstanceOf(ReflectionUtils.NoEventObjectParameterFoundException.class);
    }

    @Test
    void failsOnNoEventObjectParam() {
      assertThatThrownBy(() -> ReflectionUtils.findEventObjectParameterType(methodByName("none")))
          .isInstanceOf(ReflectionUtils.NoEventObjectParameterFoundException.class);
    }

    @Test
    void failsOnMultipleEventObjectParams() {
      assertThatThrownBy(() -> ReflectionUtils.findEventObjectParameterType(methodByName("multi")))
          .isInstanceOf(ReflectionUtils.AmbiguousObjectParameterFoundException.class);
    }

    @Test
    void findsType() {
      assertThat(ReflectionUtils.findEventObjectParameterType(methodByName("apply")))
          .isSameAs(SomeEvent.class);
    }
  }

  @Nested
  class WhenValidatingPath {
    @Accessors(fluent = false)
    @Getter
    class SomeEvent implements EventObject {
      @Override
      public Set<UUID> aggregateIds() {
        return Collections.emptySet();
      }

      A a = new A();
    }

    @Accessors(fluent = false)
    @Getter
    class A {
      B b = new B();
    }

    @Accessors(fluent = false)
    @Getter
    class B {
      UUID id = UUID.randomUUID();
    }

    @Test
    void invalidPath() {
      Assertions.assertThatThrownBy(
              () -> {
                ReflectionUtils.verifyUuidPropertyExpressionAgainstClass(
                    "a.x.y.id", SomeEvent.class);
              })
          .isInstanceOf(IllegalAggregateIdPropertyPathException.class);
    }

    @Test
    void notAUuid() {
      Assertions.assertThatThrownBy(
              () -> {
                ReflectionUtils.verifyUuidPropertyExpressionAgainstClass("a.b", SomeEvent.class);
              })
          .isInstanceOf(IllegalAggregateIdPropertyPathException.class);
    }

    @Test
    void happyPath() {
      org.junit.jupiter.api.Assertions.assertDoesNotThrow(
          () ->
              ReflectionUtils.verifyUuidPropertyExpressionAgainstClass("a.b.id", SomeEvent.class));
    }
  }
}
