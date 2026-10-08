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

import java.util.Collections;
import java.util.UUID;
import org.factcast.core.*;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.observer.FactObserver;
import org.factcast.factus.Factus;
import org.factcast.factus.projection.Aggregate;
import org.factcast.factus.projector.FactSpecProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultAggregateCacheTest {

  @Mock private Factus factus;
  @Mock private FactCast factCast;
  @Mock private FactSpecProvider specProvider;
  @Mock private Subscription subscription;

  private DefaultAggregateCache<TestAggregate> underTest;

  static class TestAggregate extends Aggregate {}

  @BeforeEach
  void setUp() {
    underTest =
        new DefaultAggregateCache<>(TestAggregate.class, builder -> builder.maximumSize(10));
  }

  @Nested
  class Operations {
    @BeforeEach
    void startCache() {
      when(factus.factCast()).thenReturn(factCast);
      when(specProvider.forSnapshot(TestAggregate.class))
          .thenReturn(Collections.singletonList(FactSpec.ns("ns").type("type")));
      underTest.start(factus, specProvider);
    }

    @Test
    void testPutAndGet() {
      UUID id = UUID.randomUUID();
      TestAggregate agg = new TestAggregate();

      underTest.put(id, agg);
      assertThat(underTest.get(id)).isSameAs(agg);
    }

    @Test
    void testSize() {
      assertThat(underTest.size()).isEqualTo(0);
      underTest.put(UUID.randomUUID(), new TestAggregate());
      assertThat(underTest.size()).isEqualTo(1);
    }

    @Test
    void testInvalidate() {
      UUID id = UUID.randomUUID();
      underTest.put(id, new TestAggregate());
      assertThat(underTest.get(id)).isNotNull();

      underTest.invalidate(id);
      assertThat(underTest.get(id)).isNull();
    }

    @Test
    void testInvalidateAll() {
      underTest.put(UUID.randomUUID(), new TestAggregate());
      underTest.put(UUID.randomUUID(), new TestAggregate());
      assertThat(underTest.size()).isEqualTo(2);

      underTest.invalidateAll();
      assertThat(underTest.size()).isEqualTo(0);
    }
  }

  @Test
  void testStart() {
    // given
    when(factus.factCast()).thenReturn(factCast);
    when(specProvider.forSnapshot(TestAggregate.class))
        .thenReturn(Collections.singletonList(FactSpec.ns("ns").type("type")));
    when(factCast.subscribe(any(), any())).thenReturn(subscription);

    // when
    underTest.start(factus, specProvider);

    // then
    verify(factCast).subscribe(any(), any());
  }

  @Test
  void testDestroy() throws Exception {
    // given
    when(factus.factCast()).thenReturn(factCast);
    when(specProvider.forSnapshot(TestAggregate.class))
        .thenReturn(Collections.singletonList(FactSpec.ns("ns").type("type")));
    when(factCast.subscribe(any(), any())).thenReturn(subscription);
    underTest.start(factus, specProvider);

    // when
    underTest.destroy();

    // then
    verify(subscription).close();
    assertThat(underTest.size()).isEqualTo(0);
    // After destroy, operations should still work but on null cache (or it should not throw)
    underTest.put(UUID.randomUUID(), new TestAggregate());
    assertThat(underTest.size()).isEqualTo(0);
  }

  @Nested
  class FactObserverTest {
    @Mock private Fact fact;
    @Mock private FactHeader header;

    private FactObserver observer;

    @BeforeEach
    void setUpObserver() {
      when(factus.factCast()).thenReturn(factCast);
      when(specProvider.forSnapshot(TestAggregate.class))
          .thenReturn(Collections.singletonList(FactSpec.ns("ns").type("type")));
      underTest.start(factus, specProvider);

      observer = underTest.createFactObserver();
    }

    @Test
    void testOnNext() {
      // given
      UUID id = UUID.randomUUID();
      underTest.put(id, new TestAggregate());
      assertThat(underTest.get(id)).isNotNull();

      when(fact.header()).thenReturn(header);
      when(header.aggIds()).thenReturn(Collections.singleton(id));

      // when
      observer.onNext(fact);

      // then
      assertThat(underTest.get(id)).isNull();
    }

    @Test
    void testOnNextWithNoAggIds() {
      // given
      UUID id = UUID.randomUUID();
      underTest.put(id, new TestAggregate());

      when(fact.header()).thenReturn(header);
      when(header.aggIds()).thenReturn(null);

      // when
      observer.onNext(fact);

      // then
      assertThat(underTest.get(id)).isNotNull();
    }

    @Test
    void testOnError() {
      // given
      underTest.put(UUID.randomUUID(), new TestAggregate());
      assertThat(underTest.size()).isEqualTo(1);

      // when
      observer.onError(new RuntimeException("boom"));

      // then
      assertThat(underTest.size()).isEqualTo(0);
      // further puts should be ignored as cache is set to null in onError
      underTest.put(UUID.randomUUID(), new TestAggregate());
      assertThat(underTest.size()).isEqualTo(0);
    }
  }

  @Test
  void testToString() {
    assertThat(underTest.toString()).contains("TestAggregate");
  }

  @Test
  void testGetAggregateType() {
    assertThat(underTest.aggregateType()).isEqualTo(TestAggregate.class);
  }

  @Test
  void testOperationsBeforeStart() {
    // Before start, cache is null
    assertThat(underTest.size()).isEqualTo(0);
    assertThat(underTest.get(UUID.randomUUID())).isNull();

    underTest.put(UUID.randomUUID(), new TestAggregate());
    assertThat(underTest.size()).isEqualTo(0);

    underTest.invalidate(UUID.randomUUID());
    underTest.invalidateAll();
    // No exception should be thrown
  }
}
