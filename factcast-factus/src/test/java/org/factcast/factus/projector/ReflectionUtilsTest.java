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
import java.lang.reflect.*;
import java.util.*;
import javax.annotation.Nullable;
import lombok.NonNull;
import lombok.SneakyThrows;
import org.assertj.core.api.Assertions;
import org.factcast.core.FactStreamPosition;
import org.factcast.core.spec.FactSpec;
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

  static class SomeUnrelatedClass {
    @FilterByAggIdProperty("narf")
    public void foo() {}
  }

  @SneakyThrows
  @Test
  void checkFilterByAggIdProperty() {
    Assertions.assertThatThrownBy(
            () ->
                ReflectionUtils.checkFilterByAggIdProperty(
                    SomeUnrelatedClass.class.getMethod("foo"), FactSpec.ns("foo")))
        .isInstanceOf(IllegalAnnotationForTargetClassException.class);
  }
}
