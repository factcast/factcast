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

import com.fasterxml.jackson.annotation.*;
import com.google.common.collect.Sets;
import jakarta.annotation.Nullable;
import java.lang.reflect.*;
import java.util.*;
import lombok.*;
import lombok.experimental.Accessors;
import org.assertj.core.api.Assertions;
import org.factcast.core.Fact;
import org.factcast.core.FactStreamPosition;
import org.factcast.factus.*;
import org.factcast.factus.event.EventObject;
import org.factcast.factus.projection.*;
import org.factcast.factus.projection.parameter.*;
import org.factcast.factus.projection.tx.*;
import org.junit.jupiter.api.*;

@SuppressWarnings("all")
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

  static class SomeUnrelatedClass {
    @FilterByAggIdProperty("narf")
    public void foo() {}
  }

  @SneakyThrows
  @Test
  void rejectsAggIdPropertyOnNonAggregate() {
    Assertions.assertThatThrownBy(
            () ->
                ReflectionUtils.discoverAggIdPropertyPath(
                    SomeUnrelatedClass.class.getMethod("foo")))
        .isInstanceOf(IllegalAnnotationForTargetClassException.class)
        .hasMessageContaining("foo");
  }

  @SneakyThrows
  @Test
  void discoversAggIdPropertyPath() {
    Method annotated =
        FilterByAggIdPropertyAggregate.class.getDeclaredMethod(
            "apply", FilterByAggIdPropertyEvent.class);
    Method plain =
        FilterByAggIdPropertyAggregate.class.getDeclaredMethod("apply", ComplexEvent.class);

    Assertions.assertThat(ReflectionUtils.discoverAggIdPropertyPath(annotated))
        .isEqualTo("recommendedUserId");
    Assertions.assertThat(ReflectionUtils.discoverAggIdPropertyPath(plain)).isNull();
  }

  @SneakyThrows
  @Test
  void rejectsAggIdPropertyOnHandlerFor() {
    Method handlerFor =
        FilterByAggIdPropertyOnHandlerForAggregate.class.getDeclaredMethod("apply", Fact.class);

    Assertions.assertThatThrownBy(() -> ReflectionUtils.discoverAggIdPropertyPath(handlerFor))
        .isInstanceOf(InvalidHandlerDefinition.class)
        .hasMessageContaining("HandlerFor");
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
      assertDoesNotThrow(
          () ->
              ReflectionUtils.verifyUuidPropertyExpressionAgainstClass("a.b.id", SomeEvent.class));
    }

    /** lombok.config in this repo defaults to fluent accessors, so there is no getId() here */
    @Getter
    class FluentEvent implements EventObject {
      @Override
      public Set<UUID> aggregateIds() {
        return Collections.emptySet();
      }

      FluentNested nested = new FluentNested();
    }

    @Getter
    class FluentNested {
      UUID id = UUID.randomUUID();
    }

    @Test
    void fallsBackToFieldsForFluentAccessors() {
      assertDoesNotThrow(
          () ->
              ReflectionUtils.verifyUuidPropertyExpressionAgainstClass(
                  "nested.id", FluentEvent.class));
    }

    class GetterOnlyEvent implements EventObject {
      @Override
      public Set<UUID> aggregateIds() {
        return Collections.emptySet();
      }

      public UUID getComputedId() {
        return UUID.randomUUID();
      }
    }

    @Test
    void resolvesGetterWithoutBackingField() {
      assertDoesNotThrow(
          () ->
              ReflectionUtils.verifyUuidPropertyExpressionAgainstClass(
                  "computedId", GetterOnlyEvent.class));
    }

    class BaseEvent implements EventObject {
      @Override
      public Set<UUID> aggregateIds() {
        return Collections.emptySet();
      }

      UUID inheritedId = UUID.randomUUID();
    }

    class SubEvent extends BaseEvent {}

    @Test
    void resolvesInheritedField() {
      assertDoesNotThrow(
          () ->
              ReflectionUtils.verifyUuidPropertyExpressionAgainstClass(
                  "inheritedId", SubEvent.class));
    }

    class RenamedPropertyEvent implements EventObject {
      @Override
      public Set<UUID> aggregateIds() {
        return Collections.emptySet();
      }

      @JsonProperty("ownerId")
      UUID internalOwnerReference = UUID.randomUUID();
    }

    /** the server matches the JSON payload, so the path must use the serialized name */
    @Test
    void resolvesJsonPropertyNameNotFieldName() {
      assertDoesNotThrow(
          () ->
              ReflectionUtils.verifyUuidPropertyExpressionAgainstClass(
                  "ownerId", RenamedPropertyEvent.class));
      Assertions.assertThatThrownBy(
              () ->
                  ReflectionUtils.verifyUuidPropertyExpressionAgainstClass(
                      "internalOwnerReference", RenamedPropertyEvent.class))
          .isInstanceOf(IllegalAggregateIdPropertyPathException.class);
    }

    class IgnoredPropertyEvent implements EventObject {
      @Override
      public Set<UUID> aggregateIds() {
        return Collections.emptySet();
      }

      @JsonIgnore UUID notInPayload = UUID.randomUUID();
    }

    /** an ignored property is never in the payload, so the server could not match it */
    @Test
    void rejectsJsonIgnoredProperty() {
      Assertions.assertThatThrownBy(
              () ->
                  ReflectionUtils.verifyUuidPropertyExpressionAgainstClass(
                      "notInPayload", IgnoredPropertyEvent.class))
          .isInstanceOf(IllegalAggregateIdPropertyPathException.class);
    }

    /** the shape of generated event classes: private fields, bean getters, jackson uses fields */
    @JsonAutoDetect(
        fieldVisibility = JsonAutoDetect.Visibility.ANY,
        getterVisibility = JsonAutoDetect.Visibility.NONE)
    @Accessors(fluent = false)
    @Getter
    class GeneratedStyleEvent implements EventObject {
      @Override
      public Set<UUID> aggregateIds() {
        return Collections.emptySet();
      }

      private GeneratedStyleMeta systemMeta = new GeneratedStyleMeta();
    }

    @JsonAutoDetect(
        fieldVisibility = JsonAutoDetect.Visibility.ANY,
        getterVisibility = JsonAutoDetect.Visibility.NONE)
    @Accessors(fluent = false)
    @Getter
    class GeneratedStyleMeta {
      private UUID actorId = UUID.randomUUID();
    }

    @Test
    void resolvesFieldsWhenGettersAreHiddenFromJackson() {
      assertDoesNotThrow(
          () ->
              ReflectionUtils.verifyUuidPropertyExpressionAgainstClass(
                  "systemMeta.actorId", GeneratedStyleEvent.class));
    }

    class JsonGetterEvent implements EventObject {
      @Override
      public Set<UUID> aggregateIds() {
        return Collections.emptySet();
      }

      private final UUID hidden = UUID.randomUUID();

      @JsonGetter("exposedId")
      public UUID hidden() {
        return hidden;
      }
    }

    /** a fluent accessor made a property via @JsonGetter is addressed by its JSON name */
    @Test
    void resolvesJsonGetterName() {
      assertDoesNotThrow(
          () ->
              ReflectionUtils.verifyUuidPropertyExpressionAgainstClass(
                  "exposedId", JsonGetterEvent.class));
    }

    @Test
    void rejectsEmptySegment() {
      Assertions.assertThatThrownBy(
              () ->
                  ReflectionUtils.verifyUuidPropertyExpressionAgainstClass(
                      "a..id", SomeEvent.class))
          .isInstanceOf(IllegalAggregateIdPropertyPathException.class);
    }
  }
}
