/*
 * Copyright © 2017-2025 factcast.org
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import com.google.common.collect.Sets;
import jakarta.annotation.Nullable;
import java.lang.reflect.*;
import java.util.*;
import lombok.Data;
import lombok.NonNull;
import lombok.SneakyThrows;
import nl.altindag.log.LogCaptor;
import nl.altindag.log.model.LogEvent;
import org.assertj.core.api.Assertions;
import org.factcast.core.FactHeader;
import org.factcast.core.FactStreamPosition;
import org.factcast.factus.*;
import org.factcast.factus.event.EventObject;
import org.factcast.factus.projection.*;
import org.factcast.factus.projection.parameter.*;
import org.factcast.factus.projection.tx.*;
import org.junit.jupiter.api.*;

class ReflectionUtilsTest {
  static class TestProjection implements SnapshotProjection {
    public TestProjection() {}
  }

  @Test
  void missingDispatcherInfo() {
    Projection p = mock(Projection.class);
    HandlerParameterContributors c = mock(HandlerParameterContributors.class);
    assertThrows(InvalidHandlerDefinition.class, () -> ReflectionUtils.getDispatcherInfo(p, c));
  }

  @Test
  void testInstantiate() {
    TestProjection instance = ReflectionUtils.instantiate(TestProjection.class);
    assertNotNull(instance);
  }

  @Test
  void testFindEventObjectParameterType() throws NoSuchMethodException {
    class Event implements EventObject {
      @Override
      public Set<UUID> aggregateIds() {
        return Sets.newHashSet();
      }
    }
    class Test {
      @Handler
      public void apply(Event e) {}
    }
    Method m = Test.class.getMethod("apply", Event.class);
    assertEquals(Event.class, ReflectionUtils.findEventObjectParameterType(m));
  }

  @Test
  void testIsEventHandlerMethodFalse() throws NoSuchMethodException {
    class Test {
      public void notAHandler(String s) {}
    }
    Method m = Test.class.getMethod("notAHandler", String.class);
    assertFalse(ReflectionUtils.isEventHandlerMethod(m));
  }

  @Test
  void testGetTypeParameter() {
    class TxAware implements OpenTransactionAware<String> {
      public String runningTransaction() {
        return "";
      }

      @Override
      public void begin() throws TransactionException {}

      @Override
      public void commit() throws TransactionException {}

      @Override
      public void rollback() throws TransactionException {}

      @Override
      public void transactionalFactStreamPosition(@NonNull FactStreamPosition factStreamPosition) {}

      @Override
      public int maxBatchSizePerTransaction() {
        return 0;
      }

      @Nullable
      @Override
      public FactStreamPosition factStreamPosition() {
        return null;
      }

      @Override
      public void factStreamPosition(@NonNull FactStreamPosition factStreamPosition) {}
    }
    assertEquals(String.class, ReflectionUtils.getTypeParameter(new TxAware()));
  }

  @Data
  static class FilterEvent implements EventObject {
    UUID userId;
    String name;
    Ref ref;

    @Override
    public Set<UUID> aggregateIds() {
      return Sets.newHashSet(userId);
    }
  }

  @Data
  static class Ref {
    UUID nestedId;
  }

  static class SomeUnrelatedClass {
    @FilterByAggIdProperty("userId")
    public void foo(FilterEvent e) {}
  }

  static class ValidAggregate extends Aggregate {
    @Handler
    @FilterByAggIdProperty("userId")
    void apply(FilterEvent e) {}

    void unannotated(FilterEvent e) {}
  }

  static class NestedPathAggregate extends Aggregate {
    @Handler
    @FilterByAggIdProperty("ref.nestedId")
    void apply(FilterEvent e) {}
  }

  static class NonUuidPropertyAggregate extends Aggregate {
    @Handler
    @FilterByAggIdProperty("name")
    void apply(FilterEvent e) {}
  }

  static class UnknownPropertyAggregate extends Aggregate {
    @Handler
    @FilterByAggIdProperty("doesNotExist")
    void apply(FilterEvent e) {}
  }

  static class HandlerForAggregate extends Aggregate {
    @HandlerFor(ns = "test", type = "FilterEvent")
    @FilterByAggIdProperty("userId")
    void apply(FilterEvent e) {}
  }

  @SneakyThrows
  @Test
  void discoverAggIdPropertyFilterRejectsNonAggregate() {
    Assertions.assertThatThrownBy(
            () ->
                ReflectionUtils.discoverAggIdPropertyFilter(
                    SomeUnrelatedClass.class.getMethod("foo", FilterEvent.class)))
        .isInstanceOf(IllegalAnnotationForTargetClassException.class);
  }

  @SneakyThrows
  @Test
  void discoverAggIdPropertyFilterDoesNotFilterHandlerForCombination() {
    LogCaptor logCaptor = LogCaptor.forClass(ReflectionUtils.class);
    Method annotatedHandlerFor =
        HandlerForAggregate.class.getDeclaredMethod("apply", FilterEvent.class);

    // no filter can be built, but the projection stays usable
    assertNull(ReflectionUtils.discoverAggIdPropertyFilter(annotatedHandlerFor));

    // ... which must not go unnoticed, as facts silently remain unfiltered
    Assertions.assertThat(logCaptor.getLogEvents()).hasSize(1);
    LogEvent logged = logCaptor.getLogEvents().get(0);
    Assertions.assertThat(logged.getLevel()).isEqualTo("ERROR");
    Assertions.assertThat(logged.getFormattedMessage())
        .contains(annotatedHandlerFor.toString())
        .contains("NO FILTERING TAKES PLACE");
  }

  @SneakyThrows
  @Test
  void discoverAggIdPropertyFilterRejectsUnknownProperty() {
    Assertions.assertThatThrownBy(
            () ->
                ReflectionUtils.discoverAggIdPropertyFilter(
                    UnknownPropertyAggregate.class.getDeclaredMethod("apply", FilterEvent.class)))
        .isInstanceOf(IllegalAggregateIdPropertyPathException.class);
  }

  @SneakyThrows
  @Test
  void discoverAggIdPropertyFilterRejectsNonUuidProperty() {
    Assertions.assertThatThrownBy(
            () ->
                ReflectionUtils.discoverAggIdPropertyFilter(
                    NonUuidPropertyAggregate.class.getDeclaredMethod("apply", FilterEvent.class)))
        .isInstanceOf(IllegalAggregateIdPropertyPathException.class);
  }

  @SneakyThrows
  @Test
  void discoverAggIdPropertyFilterReturnsNullWhenNotAnnotated() {
    assertNull(
        ReflectionUtils.discoverAggIdPropertyFilter(
            ValidAggregate.class.getDeclaredMethod("unannotated", FilterEvent.class)));
  }

  @SneakyThrows
  @Test
  void discoverAggIdPropertyFilterResolvesSimplePath() {
    AggregateIdPropertyFilter filter =
        ReflectionUtils.discoverAggIdPropertyFilter(
            ValidAggregate.class.getDeclaredMethod("apply", FilterEvent.class));
    assertNotNull(filter);
    assertEquals(1, filter.fieldChain().size());
    assertEquals("userId", filter.fieldChain().get(0).getName());
    assertEquals(0, filter.eventParameterIndex());
  }

  @SneakyThrows
  @Test
  void discoverAggIdPropertyFilterResolvesNestedPath() {
    AggregateIdPropertyFilter filter =
        ReflectionUtils.discoverAggIdPropertyFilter(
            NestedPathAggregate.class.getDeclaredMethod("apply", FilterEvent.class));
    assertNotNull(filter);
    assertEquals(2, filter.fieldChain().size());
    assertEquals("ref", filter.fieldChain().get(0).getName());
    assertEquals("nestedId", filter.fieldChain().get(1).getName());
  }

  @SneakyThrows
  @Test
  void discoverAggIdPropertyFilterFindsEventParameterIndex() {
    AggregateIdPropertyFilter filter =
        ReflectionUtils.discoverAggIdPropertyFilter(
            FilterByAggIdPropertyHeaderFirstAggregate.class.getDeclaredMethod(
                "apply", FactHeader.class, FilterByAggIdPropertyEvent.class));
    assertNotNull(filter);
    assertEquals(1, filter.eventParameterIndex());
  }

  @SneakyThrows
  @Test
  void filterMatchesOnNestedPath() {
    AggregateIdPropertyFilter filter =
        ReflectionUtils.discoverAggIdPropertyFilter(
            NestedPathAggregate.class.getDeclaredMethod("apply", FilterEvent.class));
    assertNotNull(filter);

    UUID nestedId = UUID.randomUUID();
    NestedPathAggregate aggregate = new NestedPathAggregate();
    AggregateUtil.aggregateId(aggregate, nestedId);

    FilterEvent matching = new FilterEvent();
    matching.ref(new Ref());
    matching.ref().nestedId(nestedId);
    assertTrue(filter.matches(aggregate, new Object[] {matching}));

    FilterEvent other = new FilterEvent();
    other.ref(new Ref());
    other.ref().nestedId(UUID.randomUUID());
    assertFalse(filter.matches(aggregate, new Object[] {other}));
  }

  @SneakyThrows
  @Test
  void filterSkipsOnNullIntermediateInNestedPath() {
    AggregateIdPropertyFilter filter =
        ReflectionUtils.discoverAggIdPropertyFilter(
            NestedPathAggregate.class.getDeclaredMethod("apply", FilterEvent.class));
    assertNotNull(filter);

    NestedPathAggregate aggregate = new NestedPathAggregate();
    AggregateUtil.aggregateId(aggregate, UUID.randomUUID());
    // ref is null, so the nested path cannot be resolved: no NPE, fact simply does not match
    FilterEvent event = new FilterEvent();

    assertFalse(filter.matches(aggregate, new Object[] {event}));
  }
}
