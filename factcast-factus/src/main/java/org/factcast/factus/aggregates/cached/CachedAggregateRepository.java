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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.*;
import java.util.*;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.*;
import org.factcast.core.spec.*;
import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.SubscriptionRequest;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.core.subscription.observer.FactObserver;
import org.factcast.factus.Factus;
import org.factcast.factus.aggregates.*;
import org.factcast.factus.projection.*;
import org.factcast.factus.projector.FactSpecProvider;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.SmartInitializingSingleton;

@Slf4j
@RequiredArgsConstructor
public abstract class CachedAggregateRepository<
        I extends org.factcast.factus.aggregates.AggregateIdentifier, A extends Aggregate>
    implements AggregateRepository<I, A>, DisposableBean, SmartInitializingSingleton {

  private static final int DEFAULT_CACHE_SIZE = 100;

  private final @NonNull AggregateRepository<I, A> delegate;
  private final @NonNull Factus factus;
  private final @NonNull FactSpecProvider factSpecProvider;

  private final AtomicBoolean enabled = new AtomicBoolean(true);

  private Cache<@NonNull UUID, @NonNull Optional<A>> cache;
  private Subscription subscription;

  @Override
  public void afterSingletonsInstantiated() {
    cache =
        configure(CacheBuilder.newBuilder())
            // non negotiable:
            .softValues()
            .<@NonNull UUID, @NonNull Optional<A>>build();

    @NonNull
    Collection<FactSpec> factSpecs =
        factSpecProvider.forSnapshot(delegate.aggregateType()).stream()
            .map(FactSpec::withoutAggIds)
            .collect(Collectors.toList());

    this.subscription = factus.factCast().subscribe(createRequest(factSpecs), createFactObserver());
    log.info("Done setting up cached aggregate repository: {}", this.getClass().getSimpleName());
  }

  /** hook to configure the caching behaviour */
  protected <K, V> CacheBuilder<@NonNull K, @NonNull V> configure(
      CacheBuilder<@NonNull K, @NonNull V> builder) {
    return builder.maximumSize(DEFAULT_CACHE_SIZE);
  }

  @Override
  public void destroy() throws Exception {
    if (subscription != null) {
      enabled.set(false);
      subscription.close();
      cache.invalidateAll();
    }
  }

  /**
   * Finds an aggregate by its identifier from the cache, if present otherwise fetches it from
   * factus.
   *
   * @param id the identifier of the aggregate
   * @return an optional containing the aggregate if found, or empty if not found
   */
  @SneakyThrows
  public @NonNull Optional<A> findCached(@NonNull I id) {
    if (!enabled.get()) return delegate.find(id);
    else {
      return cache.get(id.getId(), () -> delegate.find(id));
    }
  }

  public @NonNull Optional<A> find(@NonNull I id) {
    @NonNull Optional<A> agg = delegate.find(id);
    if (enabled.get()) {
      cache.put(id.getId(), agg);
    }
    return agg;
  }

  /**
   * Fetches an aggregate by its identifier from the cache, if present otherwise fetches it from
   * factus throwing an exception if not found.
   *
   * @param id the identifier of the aggregate
   * @return the aggregate if found
   * @throws org.factcast.factus.aggregates.AggregateNotFoundException found
   */
  @NonNull
  public final A fetchCached(@NonNull I id) {
    return findCached(id)
        .orElseThrow(() -> new org.factcast.factus.aggregates.AggregateNotFoundException(id));
  }

  /**
   * Fetches an aggregate by its identifier, throwing an exception if not found.
   *
   * @param id the identifier of the aggregate
   * @return the aggregate if found
   * @throws AggregateNotFoundException if the aggregate is not found
   */
  @Override
  @NonNull
  public final A fetch(@NonNull I id) {
    return find(id).orElseThrow(() -> new AggregateNotFoundException(id));
  }

  @VisibleForTesting
  @NonNull
  FactObserver createFactObserver() {
    return new FactObserver() {
      @Override
      public void onNext(@NonNull Fact f) {
        Set<UUID> uuids = f.header().aggIds();
        // yes, depending on the self-referencing issue and the implementation of
        // Projection::process, this might invalidate more than strictly necessary.
        if (uuids != null) uuids.forEach(cache::invalidate);
      }

      @Override
      public void onError(@NonNull Throwable t) {
        enabled.set(false);
        cache.invalidateAll();
        log.error("Error in subscription, falling back to uncached repository.", t);
      }
    };
  }

  @VisibleForTesting
  @NonNull
  SubscriptionRequestTO createRequest(@NonNull Collection<FactSpec> factSpecs) {
    return SubscriptionRequestTO.from(SubscriptionRequest.follow(factSpecs).fromNowOn());
  }
}
