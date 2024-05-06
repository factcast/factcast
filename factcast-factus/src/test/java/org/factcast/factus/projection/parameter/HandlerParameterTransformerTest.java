/*
 * Copyright © 2017-2024 factcast.org
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
package org.factcast.factus.projection.parameter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Method;
import lombok.SneakyThrows;
import org.assertj.core.api.Assertions;
import org.factcast.core.Fact;
import org.factcast.factus.Handler;
import org.factcast.factus.event.EventSerializer;
import org.factcast.factus.projection.LocalManagedProjection;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HandlerParameterTransformerTest {

  @Nested
  class WhenChoosingProvider {

    @SneakyThrows
    @Test
    void failsWithProperMessage() {
      Method method = SomeProjection.class.getMethod("apply", new Class[] {Fact.class, Foo.class});
      HandlerParameterContributors contribs =
          new HandlerParameterContributors(mock(EventSerializer.class));
      Assertions.assertThatThrownBy(() -> HandlerParameterTransformer.forCalling(method, contribs))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageMatching(
              "Cannot find resolver for parameter 1 of signature public void .*\\.SomeProjection\\.apply\\(.*\\.Foo\\)");
    }
  }
}

class Foo {}

class SomeProjection extends LocalManagedProjection {
  @Handler
  public void apply(Fact fact, Foo foo) {}
}
