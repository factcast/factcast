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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Method;
import org.factcast.core.Fact;
import org.factcast.core.spec.FactSpec;
import org.factcast.factus.event.EventSerializer;
import org.factcast.factus.projection.Projection;
import org.factcast.factus.projection.parameter.HandlerParameterTransformer;
import org.junit.jupiter.api.Test;

class DispatcherTest {

  static class MyProjection implements Projection {
    final Nested nested = new Nested();
  }

  static class Nested {
    Object[] lastArgs;

    public void handle(String s, Fact f) {
      lastArgs = new Object[] {s, f};
    }

    private void secret(String s) {
      // just here to be inaccessible for reflection
    }

    public void explode() {
      throw new IllegalStateException("boom");
    }
  }

  @Test
  void invokesTargetObjectWithTransformedParameters() throws Exception {
    // arrange
    EventSerializer ser = mock(EventSerializer.class);
    MyProjection projection = new MyProjection();
    Fact fact = Fact.builder().ns("ns").type("type").version(1).serial(1).buildWithoutPayload();

    Method m = Nested.class.getMethod("handle", String.class, Fact.class);
    HandlerParameterTransformer transformer = (d, f, p) -> new Object[] {"hello", f};
    ProjectorImpl.TargetObjectResolver resolver = p -> ((MyProjection) p).nested;
    FactSpec spec = FactSpec.ns("ns").type("type").version(1);

    Dispatcher underTest = new Dispatcher(m, transformer, resolver, spec);

    // act
    underTest.invoke(ser, projection, fact);

    // assert
    assertThat(projection.nested.lastArgs).isNotNull();
    assertThat(projection.nested.lastArgs[0]).isEqualTo("hello");
    assertThat(projection.nested.lastArgs[1]).isSameAs(fact);
  }

  @Test
  void wrapsIllegalAccessExceptionFromMethodInvoke() throws Exception {
    // arrange
    EventSerializer ser = mock(EventSerializer.class);
    MyProjection projection = new MyProjection();
    Fact fact = Fact.builder().ns("ns").type("type").version(1).serial(1).buildWithoutPayload();

    Method m = Nested.class.getDeclaredMethod("secret", String.class);
    // DO NOT make accessible: m.setAccessible(true);
    HandlerParameterTransformer transformer = (d, f, p) -> new Object[] {"x"};
    ProjectorImpl.TargetObjectResolver resolver = p -> ((MyProjection) p).nested;
    FactSpec spec = FactSpec.ns("ns").type("type").version(1);

    Dispatcher underTest = new Dispatcher(m, transformer, resolver, spec);

    // act/assert
    assertThatThrownBy(() -> underTest.invoke(ser, projection, fact))
        .isInstanceOf(RuntimeException.class)
        .hasCauseInstanceOf(IllegalAccessException.class);
  }

  @Test
  void unwrapsInvocationTargetExceptionAndRethrowsCause() throws Exception {
    // arrange
    EventSerializer ser = mock(EventSerializer.class);
    MyProjection projection = new MyProjection();
    Fact fact = Fact.builder().ns("ns").type("type").version(1).serial(1).buildWithoutPayload();

    Method m = Nested.class.getMethod("explode");
    HandlerParameterTransformer transformer = (d, f, p) -> new Object[0];
    ProjectorImpl.TargetObjectResolver resolver = p -> ((MyProjection) p).nested;
    FactSpec spec = FactSpec.ns("ns").type("type").version(1);

    Dispatcher underTest = new Dispatcher(m, transformer, resolver, spec);

    // act/assert
    assertThatThrownBy(() -> underTest.invoke(ser, projection, fact))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("boom");
  }
}
