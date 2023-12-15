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
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.*;
import org.assertj.core.util.Maps;
import org.factcast.core.Fact;
import org.factcast.core.FactHeader;
import org.factcast.core.event.EventConverter;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.util.FactCastJson;
import org.factcast.factus.*;
import org.factcast.factus.event.DefaultEventSerializer;
import org.factcast.factus.event.EventSerializer;
import org.factcast.factus.projection.LocalManagedProjection;
import org.factcast.factus.projection.LocalSupport;
import org.factcast.factus.projection.Projection;
import org.factcast.factus.projection.parameter.HandlerParameterContributors;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Blackbox test, we are wiring real objects into the test class, no mocks. */
// TODO
class ProjectorImplTest {

  private final DefaultEventSerializer eventSerializer =
      new DefaultEventSerializer(FactCastJson.mapper());
  private final HandlerParameterContributors contributors =
      new HandlerParameterContributors(eventSerializer);

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

      ProjectorImpl<SimpleProjection, ?> underTest =
          new ProjectorImpl<>(
              projection, eventSerializer, new HandlerParameterContributors(eventSerializer));

      // RUN
      underTest.apply(fact);

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

      DefaultProjectorFactory factory = new DefaultProjectorFactory(eventSerializer, contributors);
      Projector<SimpleProjection, ?> underTest = factory.create(projection);

      // RUN
      underTest.apply(fact);

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

      ProjectorImpl<ComplexProjection, ?> underTest =
          new ProjectorImpl<>(projection, eventSerializer, contributors);

      // RUN
      underTest.apply(fact);
      underTest.apply(fact2);

      // ASSERT
      assertThat(projection.recordedEvent()).isEqualTo(event);

      assertThat(projection.recordedEvent2()).isEqualTo(event2);
    }

    @Test
    void applyWithStaticSubclass() {
      SimpleProjectionWithStaticSubclass projection = new SimpleProjectionWithStaticSubclass();

      assertThatThrownBy(() -> new ProjectorImpl<>(projection, eventSerializer, contributors))
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

      ProjectorImpl<ComplexProjection, ?> underTest =
          new ProjectorImpl<>(projection, eventSerializer, contributors);

      // RUN / ASSERT
      assertThatThrownBy(() -> underTest.apply(fact))
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

      ProjectorImpl<ProjectionWithHandlerFor, ?> underTest =
          new ProjectorImpl<>(projection, eventSerializer, contributors);

      assertThat(projection.factId()).isNull();
      assertThat(projection.fact()).isNull();
      assertThat(projection.factHeader()).isNull();

      // RUN
      underTest.apply(fact);

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
      UUID factStreamPosition = UUID.fromString("9258562c-e6aa-4855-a765-3b1f49a113d5");

      ComplexProjection projection = new ComplexProjection();
      projection.factStreamPosition(factStreamPosition, new LocalSupport.LocalProjectorContext());

      ProjectorImpl<ComplexProjection, ?> underTest =
          new ProjectorImpl<>(projection, eventSerializer, contributors);

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

      ProjectorImpl<ComplexAggregate, ?> underTest =
          new ProjectorImpl<>(aggregate, eventSerializer, contributors);

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

      ProjectorImpl<ProjectionWithHandlerFor, ?> underTest =
          new ProjectorImpl<>(projection, eventSerializer, contributors);

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
      PostProcessingProjection p = new PostProcessingProjection(null);
      ProjectorImpl<PostProcessingProjection, ?> underTest =
          new ProjectorImpl<>(p, eventSerializer, contributors);

      // RUN
      assertThatThrownBy(() -> underTest.createFactSpecs())
          // ASSERT
          .isInstanceOf(InvalidHandlerDefinition.class)
          .hasMessageStartingWith("No FactSpecs discovered from");
    }

    @Test
    void invalidPostprocessReturnsEmptyList() {
      // INIT
      PostProcessingProjection p = new PostProcessingProjection(Collections.emptyList());
      ProjectorImpl<PostProcessingProjection, ?> underTest =
          new ProjectorImpl<>(p, eventSerializer, contributors);

      // RUN
      assertThatThrownBy(() -> underTest.createFactSpecs())
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
              () ->
                  new ProjectorImpl<>(
                      new DuplicateHandlerProjection(), eventSerializer, contributors))
          // ASSERT
          .isInstanceOf(InvalidHandlerDefinition.class)
          .hasMessageStartingWith("Duplicate Handler method found for spec");
    }

    @Test
    void duplicateArgumentHandler() {
      // INIT / RUN
      assertThatThrownBy(
              () ->
                  new ProjectorImpl<>(
                      new DuplicateArgumentProjection(), eventSerializer, contributors))
          // ASSERT
          .isInstanceOf(InvalidHandlerDefinition.class)
          .hasMessageStartingWith("Multiple EventPojo Parameters");
    }

    @Test
    void handlerWithoutParamters() {
      // INIT / RUN
      assertThatThrownBy(
              () ->
                  new ProjectorImpl<>(
                      new HandlerWithoutParameters(), eventSerializer, contributors))
          // ASSERT
          .isInstanceOf(InvalidHandlerDefinition.class)
          .hasMessageStartingWith("Handler methods must have at least one parameter");
    }

    @Test
    void handlerWithoutEventObjectParameters() {
      // INIT / RUN
      assertThatThrownBy(
              () ->
                  new ProjectorImpl<>(
                      new HandlerWithoutEventProjection(), eventSerializer, contributors))
          // ASSERT
          .isInstanceOf(InvalidHandlerDefinition.class)
          .hasMessageStartingWith("Cannot introspect FactSpec from");
    }

    @Test
    void nonVoidEventHandler() {
      // INIT / RUN
      assertThatThrownBy(
              () -> new ProjectorImpl<>(new NonVoidEventHandler(), eventSerializer, contributors))
          // ASSERT
          .isInstanceOf(InvalidHandlerDefinition.class)
          .hasMessageStartingWith("Handler methods must return void");
    }

    @Test
    void unknownHandlerParamType() {
      // INIT / RUN
      assertThatThrownBy(
              () ->
                  new ProjectorImpl<>(
                      new HandlerWithUnknownParameterType(), eventSerializer, contributors))
          // ASSERT
          .isInstanceOf(InvalidHandlerDefinition.class)
          .hasMessageStartingWith("Cannot introspect FactSpec from");
    }

    @Test
    void noHandler() {
      // INIT / RUN
      assertThatThrownBy(
              () -> new ProjectorImpl<>(new NoHandlerProjection(), eventSerializer, contributors))
          // ASSERT
          .isInstanceOf(InvalidHandlerDefinition.class)
          .hasMessageStartingWith("No handler methods discovered on");
    }

    @Test
    void handlerWithUnspecificVersion() {
      ProjectorImpl<HandlerWithoutVersionProjection, ?> p =
          new ProjectorImpl<>(new HandlerWithoutVersionProjection(), eventSerializer, contributors);

      assertThatNoException()
          .isThrownBy(
              () -> {
                Fact f1 = Fact.builder().ns("ns").type("type").version(1).buildWithoutPayload();
                Fact f2 = Fact.builder().ns("ns").type("type").version(2).buildWithoutPayload();

                p.apply(f1);
                p.apply(f2);
              });
    }
  }

  @Nested
  class WhenResolvingTargets {

    @Test
    void resolveTargetFromStaticClass() {
      assertThat(ProjectorImpl.ReflectionTools.resolveTargetObject(this, StaticClass.class))
          .isNotNull()
          .isInstanceOf(StaticClass.class);
    }

    @Test
    void resolveTargetFromNonStaticClass() {
      assertThat(
              ProjectorImpl.ReflectionTools.resolveTargetObject(
                  ProjectorImplTest.this, NonStaticClass.class))
          .isNotNull()
          .isInstanceOf(NonStaticClass.class);
    }
  }

  @Nested
  class WhenTestingForHandlerMethods {
    final ProjectorImpl<NonStaticClass, ?> underTest =
        new ProjectorImpl<>(new NonStaticClass(), mock(EventSerializer.class), contributors);

    @Test
    void matchesHandlerMethods() throws NoSuchMethodException {
      Method realMethod = NonStaticClass.class.getDeclaredMethod("apply", SimpleEvent.class);
      assertThat(underTest.isEventHandlerMethod(realMethod, contributors)).isTrue();
    }

    @Test
    void ignoresMockitoMockProvidedMethods() throws NoSuchMethodException {
      Method realMethod =
          NonStaticClass$MockitoMock.class.getDeclaredMethod("apply", SimpleEvent.class);
      assertThat(underTest.isEventHandlerMethod(realMethod, contributors)).isFalse();
    }
  }

  @Nested
  class WhenTestingCatchingUp {

    @Test
    @Disabled("is this necessary?")
    void setsFactStreamPosition() throws NoSuchMethodException {

      LocalManagedProjection factStreamPositionAware =
          spy(
              new LocalManagedProjection() {
                @Handler
                void apply(SimpleEvent e) {}
              });
      ProjectorImpl<LocalManagedProjection, ?> underTest =
          new ProjectorImpl<>(factStreamPositionAware, mock(EventSerializer.class), contributors);

      UUID id = UUID.randomUUID();
      underTest.onCatchup(id);

      verify(factStreamPositionAware).factStreamPosition(same(id), any());
    }

    @Test
    @Disabled("See above, about to remove the setting of FSP on catchup altogether")
    void skipsSettingFactStreamPosition() throws NoSuchMethodException {
      Projection notFactStreamPositionAware =
          spy(
              new Projection() {
                @Handler
                void apply(SimpleEvent e) {}
              });
      ProjectorImpl<Projection, ?> underTest =
          new ProjectorImpl<>(
              notFactStreamPositionAware, mock(EventSerializer.class), contributors);

      UUID id = UUID.randomUUID();
      underTest.onCatchup(id);

      verifyNoInteractions(notFactStreamPositionAware);
    }
  }

  @SuppressWarnings("InnerClassMayBeStatic")
  class NonStaticClass implements Projection {
    @Handler
    void apply(SimpleEvent e) {}
  }

  class NonStaticClass$MockitoMock extends NonStaticClass {
    @Override
    @Handler
    void apply(SimpleEvent e) {}
  }

  static class StaticClass {}

  // Working handlers

  @EqualsAndHashCode(callSuper = true)
  @Value
  static class PostProcessingProjection extends LocalManagedProjection {

    private final List<FactSpec> factSpecs;

    @Override
    public @NonNull List<FactSpec> postprocess(@NonNull List<FactSpec> specsAsDiscovered) {
      return factSpecs;
    }

    @Handler
    void handle(SimpleEvent event) {
      // nothing
    }
  }

  //
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

  //
  //      // Faulty handlers
  //
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

  static class HandlerMethodsWithAdditionalFilters {
    @HandlerFor(ns = "ns", type = "type")
    @FilterByMeta(key = "foo", value = "bar")
    public void applyWithOneMeta(Fact f) {}

    @HandlerFor(ns = "ns", type = "type")
    @FilterByMeta(key = "foo", value = "bar")
    @FilterByMeta(key = "bar", value = "baz")
    public void applyWithMultiMeta(Fact f) {}

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
    ProjectorImpl.ReflectionTools.addOptionalFilterInfo(m, spec);

    assertThat(spec.meta()).containsEntry("foo", "bar");
    assertThat(spec.meta()).hasSize(1);
  }

  @SneakyThrows
  @Test
  void detectsMultiMeta() {
    FactSpec spec = FactSpec.ns("ns");
    Method m =
        HandlerMethodsWithAdditionalFilters.class.getMethod("applyWithMultiMeta", Fact.class);
    ProjectorImpl.ReflectionTools.addOptionalFilterInfo(m, spec);

    assertThat(spec.meta()).containsEntry("foo", "bar");
    assertThat(spec.meta()).containsEntry("bar", "baz");
    assertThat(spec.meta()).hasSize(2);
  }

  @SneakyThrows
  @Test
  void detectsAggId() {
    FactSpec spec = FactSpec.ns("ns");
    Method m = HandlerMethodsWithAdditionalFilters.class.getMethod("applyWithAggId", Fact.class);
    ProjectorImpl.ReflectionTools.addOptionalFilterInfo(m, spec);

    assertThat(spec.aggId()).isEqualTo(UUID.fromString("1010a955-04a2-417b-9904-f92f88fdb67d"));
  }

  //
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

  static class TxProjection extends LocalManagedProjection {
    @Handler
    void apply(SimpleEvent e) {}
  }

  @Nested
  class TransactionalBehavior {
    @SneakyThrows
    @Test
    void singleFactAsList() {
      TxProjection p = spy(new TxProjection());
      ProjectorImpl<TxProjection, LocalSupport.LocalProjectorContext> uut =
          new ProjectorImpl<>(p, eventSerializer, contributors);
      SimpleEvent event = new SimpleEvent(Maps.newHashMap("foo", "bar"), UUID.randomUUID(), "code");
      List<Fact> msg = Lists.newArrayList(eventConverter.toFact(event));
      uut.apply(msg);

      verify(p, times(1)).begin();
      verify(p, times(1)).apply(eq(event));
      verify(p, times(1)).commit(any());
    }

    @SneakyThrows
    @Test
    void singleFact() {
      TxProjection p = spy(new TxProjection());
      ProjectorImpl<TxProjection, LocalSupport.LocalProjectorContext> uut =
          new ProjectorImpl<>(p, eventSerializer, contributors);
      SimpleEvent event = new SimpleEvent(Maps.newHashMap("foo", "bar"), UUID.randomUUID(), "code");

      uut.apply(eventConverter.toFact(event));

      verify(p, times(1)).begin();
      verify(p, times(1)).apply(eq(event));
      verify(p, times(1)).commit(any());
    }

    @SneakyThrows
    @Test
    void mutipleFactsAsList() {
      TxProjection p = spy(new TxProjection());
      ProjectorImpl<TxProjection, LocalSupport.LocalProjectorContext> uut =
          new ProjectorImpl<>(p, eventSerializer, contributors);
      SimpleEvent e1 = new SimpleEvent(Maps.newHashMap("foo", "bar"), UUID.randomUUID(), "code");
      SimpleEvent e2 = new SimpleEvent(Maps.newHashMap("foo", "bar"), UUID.randomUUID(), "code2");
      List<Fact> msg = Lists.newArrayList(eventConverter.toFact(e1), eventConverter.toFact(e2));
      uut.apply(msg);

      verify(p, times(1)).begin();
      verify(p, times(2)).apply(any());
      verify(p, times(1)).commit(any());
    }

    @SneakyThrows
    @Test
    void singleFactWithRollback() {
      TxProjection p = spy(new TxProjection());
      ProjectorImpl<TxProjection, LocalSupport.LocalProjectorContext> uut =
          new ProjectorImpl<>(p, eventSerializer, contributors);
      SimpleEvent e1 = new SimpleEvent(Maps.newHashMap("foo", "bar"), UUID.randomUUID(), "code");
      List<Fact> msg = Lists.newArrayList(eventConverter.toFact(e1));

      doThrow(IllegalStateException.class).when(p).apply(any()); // cause the rollback
      doNothing().when(p).rollback(any());

      assertThatThrownBy(
              () -> {
                uut.apply(msg);
              })
          .isInstanceOf(IllegalStateException.class);

      verify(p, times(1)).begin();
      verify(p, times(1)).apply(any());
      verify(p, times(1)).rollback(any());
      verify(p, never()).commit(any());
    }

    @SneakyThrows
    @Test
    void retryFromFailure() {
      TxProjection p = spy(new TxProjection());
      ProjectorImpl<TxProjection, LocalSupport.LocalProjectorContext> uut =
          new ProjectorImpl<>(p, eventSerializer, contributors);
      SimpleEvent e1 = new SimpleEvent(Maps.newHashMap("foo", "bar"), UUID.randomUUID(), "code");
      SimpleEvent e2 = new SimpleEvent(Maps.newHashMap("foo", "bar"), UUID.randomUUID(), "code");
      SimpleEvent e3 = new SimpleEvent(Maps.newHashMap("foo", "bar"), UUID.randomUUID(), "code");
      List<Fact> msg =
          Lists.newArrayList(
              eventConverter.toFact(e1), eventConverter.toFact(e2), eventConverter.toFact(e3));

      doThrow(IllegalStateException.class).when(p).apply(e3); // cause the retry
      doNothing().when(p).rollback(any());

      // should apply e1,e2,e3 in one tx - fail on e3, then restart e1 & e2 in single tx
      assertThatThrownBy(
              () -> {
                uut.apply(msg);
              })
          .isInstanceOf(RuntimeException.class);

      verify(p, times(2)).begin();
      verify(p, times(1)).rollback(any());
      verify(p, times(1)).commit(any());
      verify(p, times(5)).apply(any());
      verify(p, times(2)).apply(e1);
      verify(p, times(2)).apply(e2);
      verify(p, times(1)).apply(e3);
    }
  }
}
