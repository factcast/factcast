/*
 * Copyright © 2017-2026 factcast.org
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
package org.factcast.store.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.factcast.core.spec.FactSpec;
import org.factcast.store.internal.notification.FactInsertionNotification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QueryExecutorTest {

  @Mock private PgSynchronizedQuery callback;
  @Mock private Supplier<Boolean> connectionStateSupplier;

  private QueryExecutor uut;

  @Nested
  class WhenExecuting {

    @BeforeEach
    void setUp() {
      uut = new QueryExecutor(callback, connectionStateSupplier, List.of(FactSpec.ns("ns")));
    }

    @Test
    void triggersWhenConnectionIsActive() {
      when(connectionStateSupplier.get()).thenReturn(true);
      uut.trigger();
      verify(callback).run(false);
    }

    @Test
    void doesNotTriggerWhenConnectionIsInactive() {
      when(connectionStateSupplier.get()).thenReturn(false);
      uut.trigger();
      verify(callback, never()).run(anyBoolean());
    }

    @Test
    void onEventTriggersWhenMightMatch() {
      when(connectionStateSupplier.get()).thenReturn(true);
      FactInsertionNotification ev = FactInsertionNotification.internal("ns", "type");
      uut.onEvent(ev);
      verify(callback).run(false);
    }

    @Test
    void onEventDoesNotTriggerWhenMightNotMatch() {
      FactInsertionNotification ev = FactInsertionNotification.internal("otherNs", "type");
      uut.onEvent(ev);
      verify(callback, never()).run(anyBoolean());
    }
  }

  @Nested
  class WhenMatchingWildcards {
    @ParameterizedTest
    @MethodSource("specsForMatching")
    void interestsUsingWildcardsMatchCorrectly(
        String nsMatcher, String typeMatcher, String ns, String type, boolean shouldMatch) {
      FactSpec spec;
      if (typeMatcher == null) {
        spec = FactSpec.ns(nsMatcher);
      } else {
        spec = FactSpec.ns(nsMatcher).type(typeMatcher);
      }

      QueryExecutor uut = new QueryExecutor(callback, connectionStateSupplier, List.of(spec));

      final var res = uut.mightMatch(ns, type);

      assertThat(res).isEqualTo(shouldMatch);
    }

    static Stream<Arguments> specsForMatching() {
      return Stream.of(
          Arguments.of("*", "*", "ns", "type", true),
          Arguments.of("*", null, "ns", "type", true),
          Arguments.of("*", "type", "ns", "type", true),
          Arguments.of("*", "type", "ns", "typeFoo", false),
          Arguments.of("ns", "*", "ns", "type", true),
          Arguments.of("ns", "*", "nsFoo", "type", false),
          Arguments.of("ns", "type", "ns", "type", true),
          Arguments.of("ns", "type", "nsFoo", "type", false),
          Arguments.of("ns", "type", "ns", "typeFoo", false));
    }
  }
}
