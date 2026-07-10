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
package org.factcast.store.internal;

import static org.mockito.Mockito.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@Disabled
// TODO
public class CondensedQueryExecutorTest {
  //
  //  @Mock Timer mockTimer;
  //
  //  @Mock PgSynchronizedQuery callback;
  //
  //  @Captor ArgumentCaptor<TimerTask> task;
  //
  //  @Nested
  //  class WhenExecuting {
  //
  //    @BeforeEach
  //    void setUp() {
  //      doNothing().when(mockTimer).schedule(task.capture(), anyLong());
  //    }
  //
  //    @Test
  //    void testDelayedExecution() {
  //      QueryExecutor uut =
  //          new QueryExecutor(1, callback, () -> true, Lists.newArrayList(), mockTimer);
  //      uut.trigger();
  //      verify(mockTimer).schedule(any(), eq(1L));
  //      task.getValue().run();
  //      verify(callback).run(anyBoolean());
  //    }
  //
  //    @Test
  //    void testDelayedMultipleExecution() {
  //      QueryExecutor uut =
  //          new QueryExecutor(22, callback, () -> true, Lists.newArrayList(), mockTimer);
  //      verify(mockTimer, never()).schedule(any(), anyLong());
  //      uut.trigger();
  //      task.getAllValues().get(0).run();
  //      uut.trigger();
  //      task.getAllValues().get(1).run();
  //      verify(callback, times(2)).run(anyBoolean());
  //    }
  //
  //    @Test
  //    void testDelayedCondensedExecution() {
  //      QueryExecutor uut =
  //          new QueryExecutor(104, callback, () -> true, Lists.newArrayList(), mockTimer);
  //      // not yet scheduled anything
  //      verify(mockTimer, never()).schedule(any(), anyLong());
  //      uut.trigger();
  //      // scheduled once
  //      verify(mockTimer).schedule(any(), eq(104L));
  //      uut.trigger();
  //      uut.trigger();
  //      uut.trigger();
  //      uut.trigger();
  //      // still scheduled only once
  //      verify(mockTimer).schedule(any(), eq(104L));
  //      TimerTask taskArg = task.getValue();
  //      taskArg.run();
  //      // executing must noch change anything for scheduling
  //      verify(mockTimer).schedule(any(), eq(104L));
  //      verifyNoMoreInteractions(mockTimer);
  //      uut.trigger();
  //      // a second call is scheduled
  //      verify(mockTimer, times(2)).schedule(any(), eq(104L));
  //      uut.trigger();
  //      uut.trigger();
  //      uut.trigger();
  //      // no change: second call is scheduled
  //      verify(mockTimer, times(2)).schedule(any(), eq(104L));
  //    }
  //  }
  //
  //  @Nested
  //  class WhenMatchingWildcards {
  //    @ParameterizedTest
  //    @MethodSource("specsForMatching")
  //    void interestsUsingWildcardsMatchCorrectly(
  //        String nsMatcher, String typeMatcher, String ns, String type, boolean shouldMatch) {
  //      QueryExecutor uut =
  //          new QueryExecutor(
  //              0,
  //              callback,
  //              () -> true,
  //              List.of(FactSpec.ns(nsMatcher).type(typeMatcher)),
  //              mockTimer);
  //
  //      final var res = uut.mightMatch(ns, type);
  //
  //      assertThat(res).isEqualTo(shouldMatch);
  //    }
  //
  //    static Stream<Arguments> specsForMatching() {
  //      return Stream.of(
  //          Arguments.of("*", "*", "ns", "type", true),
  //          Arguments.of("*", null, "ns", "type", true),
  //          Arguments.of("*", "type", "ns", "type", true),
  //          Arguments.of("*", "type", "ns", "typeFoo", false),
  //          Arguments.of("ns", "*", "ns", "type", true),
  //          Arguments.of("ns", "*", "nsFoo", "type", false),
  //          Arguments.of("ns", "type", "ns", "type", true),
  //          Arguments.of("ns", "type", "nsFoo", "type", false),
  //          Arguments.of("ns", "type", "ns", "typeFoo", false));
  //    }
  //  }
}
