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
package org.factcast.factus.aggregates.cached;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.*;
import lombok.NonNull;
import org.factcast.core.*;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.SubscriptionRequest;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.core.subscription.observer.FactObserver;
import org.factcast.factus.Factus;
import org.factcast.factus.aggregates.*;
import org.factcast.factus.projection.Aggregate;
import org.factcast.factus.projector.FactSpecProvider;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;

class CachedAggregateRepositoryTest {

  // simple test aggregate and id
  static class MyAgg extends Aggregate {}

  static class MyId implements AggregateIdentifier {
    final UUID id;

    MyId(UUID id) {
      this.id = id;
    }

    @Override
    public @NonNull UUID getId() {
      return id;
    }

    @Override
    public String toString() {
      return "MyId{" + id + '}';
    }
  }

  // repository variant that uses maximumSize instead of maximumWeight for tests
  static class TestRepo extends CachedAggregateRepository<MyId, MyAgg> {
    TestRepo(AggregateRepository<MyId, MyAgg> d, Factus f, FactSpecProvider p) {
      super(d, f, p);
    }

    @Override
    protected <K, V> com.google.common.cache.CacheBuilder<K, V> configure(
        com.google.common.cache.CacheBuilder<K, V> builder) {
      // simple size-based cache to avoid the need for a weigher in tests
      return builder.maximumSize(100);
    }
  }

  @Test
  void afterSingletonsInstantiated_subscribes_and_buildsCache() {
    // arrange
    @SuppressWarnings("unchecked")
    AggregateRepository<MyId, MyAgg> delegate = mock(AggregateRepository.class);
    when(delegate.aggregateType()).thenReturn(MyAgg.class);

    FactCast factCast = mock(FactCast.class);
    Factus factus = mock(Factus.class);
    when(factus.factCast()).thenReturn(factCast);

    FactSpecProvider factSpecProvider = mock(FactSpecProvider.class);
    List<FactSpec> specs = Arrays.asList(FactSpec.ns("ns").type("t").version(1));
    when(factSpecProvider.forSnapshot(MyAgg.class)).thenReturn(specs);

    Subscription sub = mock(Subscription.class);
    ArgumentCaptor<SubscriptionRequest> reqCap = ArgumentCaptor.forClass(SubscriptionRequest.class);
    ArgumentCaptor<FactObserver> obsCap = ArgumentCaptor.forClass(FactObserver.class);
    when(factCast.subscribe(reqCap.capture(), obsCap.capture())).thenReturn(sub);

    CachedAggregateRepository<MyId, MyAgg> underTest =
        new TestRepo(delegate, factus, factSpecProvider);

    // act
    underTest.afterSingletonsInstantiated();

    // assert subscription happened with correct types
    verify(factCast).subscribe(any(SubscriptionRequestTO.class), any(FactObserver.class));
    assertThat(reqCap.getValue()).isInstanceOf(SubscriptionRequestTO.class);
    assertThat(obsCap.getValue()).isNotNull();
  }

  @Test
  void findCached_usesCache_and_disabledFallsBack() {
    @SuppressWarnings("unchecked")
    AggregateRepository<MyId, MyAgg> delegate = mock(AggregateRepository.class);
    when(delegate.aggregateType()).thenReturn(MyAgg.class);
    Factus factus = mock(Factus.class);
    when(factus.factCast()).thenReturn(mock(FactCast.class));
    FactSpecProvider factSpecProvider = mock(FactSpecProvider.class);
    when(factSpecProvider.forSnapshot(MyAgg.class))
        .thenReturn(Arrays.asList(FactSpec.ns("ns").type("t").version(1)));

    CachedAggregateRepository<MyId, MyAgg> repo = new TestRepo(delegate, factus, factSpecProvider);
    repo.afterSingletonsInstantiated();

    UUID id = UUID.randomUUID();
    MyId myId = new MyId(id);
    MyAgg a = new MyAgg();
    when(delegate.find(myId)).thenReturn(Optional.of(a));

    // first call queries delegate and caches
    Optional<MyAgg> r1 = repo.findCached(myId);
    // second call should be served from cache, so still one invocation
    Optional<MyAgg> r2 = repo.findCached(myId);

    assertThat(r1).containsSame(a);
    assertThat(r2).containsSame(a);
    verify(delegate, times(1)).find(myId);

    // disable via observer error to simulate fallback
    // grab the observer by re-subscribing (simpler than reflection)
    FactCast factCast = mock(FactCast.class);
    when(factus.factCast()).thenReturn(factCast);
    ArgumentCaptor<FactObserver> obsCap = ArgumentCaptor.forClass(FactObserver.class);
    when(factCast.subscribe(any(), obsCap.capture())).thenReturn(mock(Subscription.class));
    // re-init to register a real observer we can invoke
    repo.afterSingletonsInstantiated();
    FactObserver obs = obsCap.getValue();
    obs.onError(new RuntimeException("fail"));

    // now cache is disabled, delegate called again
    repo.findCached(myId);
    verify(delegate, times(2)).find(myId);
  }

  @Test
  void find_putsIntoCache_whenEnabled() {
    @SuppressWarnings("unchecked")
    AggregateRepository<MyId, MyAgg> delegate = mock(AggregateRepository.class);
    when(delegate.aggregateType()).thenReturn(MyAgg.class);
    Factus factus = mock(Factus.class);
    when(factus.factCast()).thenReturn(mock(FactCast.class));
    FactSpecProvider factSpecProvider = mock(FactSpecProvider.class);
    when(factSpecProvider.forSnapshot(MyAgg.class))
        .thenReturn(Arrays.asList(FactSpec.ns("ns").type("t").version(1)));

    CachedAggregateRepository<MyId, MyAgg> repo = new TestRepo(delegate, factus, factSpecProvider);
    repo.afterSingletonsInstantiated();

    UUID id = UUID.randomUUID();
    MyId myId = new MyId(id);
    MyAgg a = new MyAgg();
    when(delegate.find(myId)).thenReturn(Optional.of(a));

    // find() should populate cache
    Optional<MyAgg> r1 = repo.find(myId);
    assertThat(r1).containsSame(a);
    // next call should hit cache
    Optional<MyAgg> r2 = repo.findCached(myId);
    assertThat(r2).containsSame(a);
    verify(delegate, times(1)).find(myId);
  }

  @Test
  void fetch_and_fetchCached_throwOnEmpty() {
    @SuppressWarnings("unchecked")
    AggregateRepository<MyId, MyAgg> delegate = mock(AggregateRepository.class);
    when(delegate.aggregateType()).thenReturn(MyAgg.class);
    Factus factus = mock(Factus.class);
    when(factus.factCast()).thenReturn(mock(FactCast.class));
    FactSpecProvider factSpecProvider = mock(FactSpecProvider.class);
    when(factSpecProvider.forSnapshot(MyAgg.class))
        .thenReturn(Arrays.asList(FactSpec.ns("ns").type("t").version(1)));

    CachedAggregateRepository<MyId, MyAgg> repo = new TestRepo(delegate, factus, factSpecProvider);
    repo.afterSingletonsInstantiated();

    MyId myId = new MyId(UUID.randomUUID());
    when(delegate.find(myId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> repo.fetch(myId)).isInstanceOf(AggregateNotFoundException.class);
    assertThatThrownBy(() -> repo.fetchCached(myId)).isInstanceOf(AggregateNotFoundException.class);
  }

  @Test
  void observer_onNext_invalidatesAggIds_and_onError_disablesAndClears() {
    @SuppressWarnings("unchecked")
    AggregateRepository<MyId, MyAgg> delegate = mock(AggregateRepository.class);
    when(delegate.aggregateType()).thenReturn(MyAgg.class);
    Factus factus = mock(Factus.class);
    FactCast factCast = mock(FactCast.class);
    when(factus.factCast()).thenReturn(factCast);
    FactSpecProvider factSpecProvider = mock(FactSpecProvider.class);
    when(factSpecProvider.forSnapshot(MyAgg.class))
        .thenReturn(Arrays.asList(FactSpec.ns("ns").type("t").version(1)));

    ArgumentCaptor<FactObserver> obsCap = ArgumentCaptor.forClass(FactObserver.class);
    when(factCast.subscribe(any(), obsCap.capture())).thenReturn(mock(Subscription.class));

    CachedAggregateRepository<MyId, MyAgg> repo = new TestRepo(delegate, factus, factSpecProvider);
    repo.afterSingletonsInstantiated();

    UUID id = UUID.randomUUID();
    MyId myId = new MyId(id);
    MyAgg a = new MyAgg();
    when(delegate.find(myId)).thenReturn(Optional.of(a));

    // populate cache
    repo.findCached(myId);
    verify(delegate, times(1)).find(myId);

    // onNext with matching aggId should invalidate -> causes another delegate call
    Fact f = Fact.builder().ns("ns").type("t").version(1).serial(1).aggId(id).buildWithoutPayload();
    obsCap.getValue().onNext(f);
    repo.findCached(myId);
    verify(delegate, times(2)).find(myId);

    // onError disables + clears -> next call delegates again
    obsCap.getValue().onError(new RuntimeException("x"));
    repo.findCached(myId);
    verify(delegate, times(3)).find(myId);
  }

  @Test
  void destroy_closesSubscription_and_invalidatesCache() throws Exception {
    @SuppressWarnings("unchecked")
    AggregateRepository<MyId, MyAgg> delegate = mock(AggregateRepository.class);
    when(delegate.aggregateType()).thenReturn(MyAgg.class);
    Factus factus = mock(Factus.class);
    FactCast factCast = mock(FactCast.class);
    when(factus.factCast()).thenReturn(factCast);
    FactSpecProvider factSpecProvider = mock(FactSpecProvider.class);
    when(factSpecProvider.forSnapshot(MyAgg.class))
        .thenReturn(Arrays.asList(FactSpec.ns("ns").type("t").version(1)));

    Subscription sub = mock(Subscription.class);
    when(factCast.subscribe(any(), any())).thenReturn(sub);

    CachedAggregateRepository<MyId, MyAgg> repo = new TestRepo(delegate, factus, factSpecProvider);
    repo.afterSingletonsInstantiated();

    // prime cache
    MyId id = new MyId(UUID.randomUUID());
    when(delegate.find(id)).thenReturn(Optional.of(new MyAgg()));
    repo.findCached(id);

    // act
    repo.destroy();

    // assert
    verify(sub).close();
    // subsequent findCached after destroy should directly delegate (cache cleared & disabled)
    repo.findCached(id);
    verify(delegate, times(2)).find(id);
  }
}
