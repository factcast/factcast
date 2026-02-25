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
package org.factcast.factus.aggregate.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.*;
import org.factcast.core.FactCast;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.subscription.Subscription;
import org.factcast.factus.Factus;
import org.factcast.factus.projection.Aggregate;
import org.factcast.factus.projector.FactSpecProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AggregateCacheFactoryTest {

  @Mock private Factus factus;
  @Mock private FactCast factCast;
  @Mock private FactSpecProvider specProvider;
  @Mock private Subscription subscription;

  private AggregateCacheFactory underTest;

  static class TestAggregate extends Aggregate {}

  static class TestAggregate2 extends Aggregate {}

  @BeforeEach
  void setUp() {
    when(factus.factCast()).thenReturn(factCast);
    underTest = new AggregateCacheFactory(factus, specProvider);
  }

  @Test
  void testCreate_withSize() {
    // given
    when(specProvider.forSnapshot(TestAggregate.class))
        .thenReturn(Collections.singletonList(FactSpec.ns("ns").type("type")));
    when(factCast.subscribe(any(), any())).thenReturn(subscription);

    // when
    AggregateCache<TestAggregate> result = underTest.create(TestAggregate.class, 7);

    // then
    assertThat(result).isNotNull();
    assertThat(result.aggregateType()).isEqualTo(TestAggregate.class);
    verify(factCast).subscribe(any(), any());

    assertThat(result.size()).isEqualTo(0);
    for (int i = 0; i < 10; i++) {
      result.put(UUID.randomUUID(), new TestAggregate());
    }
    assertThat(result.size()).isEqualTo(7);
  }

  @Test
  void testDestroy() throws Exception {
    // given
    when(specProvider.forSnapshot(TestAggregate.class))
        .thenReturn(Collections.singletonList(FactSpec.ns("ns").type("type")));
    when(factCast.subscribe(any(), any())).thenReturn(subscription);

    underTest.create(TestAggregate.class);

    // when
    underTest.destroy();

    // then
    verify(subscription, times(1)).close();
  }

  @Test
  void testDestroy_handlesException() throws Exception {
    // given
    when(specProvider.forSnapshot(TestAggregate.class))
        .thenReturn(Collections.singletonList(FactSpec.ns("ns").type("type")));

    when(specProvider.forSnapshot(TestAggregate2.class))
        .thenReturn(Collections.singletonList(FactSpec.ns("ns").type("type2")));

    when(factCast.subscribe(any(), any())).thenReturn(subscription);

    underTest.create(TestAggregate.class);
    underTest.create(TestAggregate2.class);

    doThrow(new RuntimeException("boom")).when(subscription).close();

    // when
    underTest.destroy();

    // then
    verify(subscription, times(2)).close();
    // should not throw exception
  }
}
