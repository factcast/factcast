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
package org.factcast.factus.aggregate.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.google.common.cache.CacheBuilder;
import java.util.*;
import lombok.NonNull;
import org.factcast.core.*;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.core.subscription.observer.FactObserver;
import org.factcast.factus.Factus;
import org.factcast.factus.projection.Aggregate;
import org.factcast.factus.projector.FactSpecProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AbstractAggregateCacheTest {

  static class TestAggregate extends Aggregate {}

  static class TestCache extends AbstractAggregateCache<TestAggregate> {

    TestCache(@NonNull Factus factus, @NonNull FactSpecProvider factSpecProvider) {
      super(factus, factSpecProvider);
    }

    // make cache small and deterministic
    @Override
    protected <K, V> CacheBuilder<@NonNull K, @NonNull V> configure(
        CacheBuilder<@NonNull K, @NonNull V> builder) {
      return builder.maximumSize(10);
    }
  }

  @Mock Factus factus;
  @Mock FactCast factCast;
  @Mock FactSpecProvider factSpecProvider;
  @Mock Subscription subscription;

  TestCache underTest;

  @BeforeEach
  void setup() {
    when(factus.factCast()).thenReturn(factCast);
    underTest = new TestCache(factus, factSpecProvider);
  }

  @Test
  void afterSingletonsInstantiated_subscribesAndResolvesType() {
    // given
    FactSpec withAgg = FactSpec.ns("ns").type("t").version(1).aggId(UUID.randomUUID());
    when(factSpecProvider.forSnapshot(TestAggregate.class))
        .thenReturn(Collections.singletonList(withAgg));

    ArgumentCaptor<SubscriptionRequestTO> reqCap =
        ArgumentCaptor.forClass(SubscriptionRequestTO.class);
    ArgumentCaptor<FactObserver> obsCap = ArgumentCaptor.forClass(FactObserver.class);
    when(factCast.subscribe(any(), any())).thenReturn(subscription);

    // when
    underTest.afterSingletonsInstantiated();

    // then
    assertThat(underTest.aggregateType()).isEqualTo(TestAggregate.class);
    verify(factCast).subscribe(reqCap.capture(), obsCap.capture());
    // request should be created and non-null
    assertThat(reqCap.getValue()).isNotNull();
    assertThat(obsCap.getValue()).isNotNull();
  }

  @Test
  void getPut_enabledAndDisabled() {
    // given
    when(factSpecProvider.forSnapshot(TestAggregate.class))
        .thenReturn(Collections.singletonList(FactSpec.ns("ns").type("t")));
    when(factCast.subscribe(any(), any())).thenReturn(subscription);
    underTest.afterSingletonsInstantiated();

    UUID id = UUID.randomUUID();
    TestAggregate agg = new TestAggregate();

    // enabled -> stores and returns
    underTest.put(id, agg);
    assertThat(underTest.get(id)).isSameAs(agg);

    // disable by simulating onError from the observer
    FactObserver observer = underTest.createFactObserver();
    observer.onError(new RuntimeException("boom"));

    // disabled -> get returns null, put does nothing
    assertThat(underTest.get(id)).isNull();
    underTest.put(UUID.randomUUID(), new TestAggregate());
    assertThat(underTest.get(id)).isNull();
  }

  @Test
  void destroy_closesSubscriptionAndClearsCache() throws Exception {
    // given
    when(factSpecProvider.forSnapshot(TestAggregate.class))
        .thenReturn(Collections.singletonList(FactSpec.ns("ns").type("t")));
    when(factCast.subscribe(any(), any())).thenReturn(subscription);
    underTest.afterSingletonsInstantiated();

    UUID id = UUID.randomUUID();
    TestAggregate agg = new TestAggregate();
    underTest.put(id, agg);
    assertThat(underTest.get(id)).isSameAs(agg);

    // when
    underTest.destroy();

    // then
    verify(subscription).close();
    assertThat(underTest.get(id)).isNull();
  }

  @Test
  void observer_onNextInvalidatesAggIds() {
    // prepare cache
    when(factSpecProvider.forSnapshot(TestAggregate.class))
        .thenReturn(Collections.singletonList(FactSpec.ns("ns").type("t")));
    when(factCast.subscribe(any(), any())).thenReturn(subscription);
    underTest.afterSingletonsInstantiated();

    UUID id1 = UUID.randomUUID();
    UUID id2 = UUID.randomUUID();
    underTest.put(id1, new TestAggregate());
    underTest.put(id2, new TestAggregate());
    assertThat(underTest.get(id1)).isNotNull();
    assertThat(underTest.get(id2)).isNotNull();

    // create fact with both aggIds via builder
    Fact fact = Fact.builder().ns("ns").type("t").aggId(id1).aggId(id2).build("{}");

    FactObserver obs = underTest.createFactObserver();
    obs.onNext(fact);

    // then: both invalidated
    assertThat(underTest.get(id1)).isNull();
    assertThat(underTest.get(id2)).isNull();
  }
}
