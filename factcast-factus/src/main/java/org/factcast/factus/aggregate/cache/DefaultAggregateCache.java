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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.Fact;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.subscription.*;
import org.factcast.core.subscription.observer.FactObserver;
import org.factcast.factus.Factus;
import org.factcast.factus.projection.Aggregate;
import org.factcast.factus.projector.FactSpecProvider;

@Slf4j
@ToString(of = "aggregateType")
public class DefaultAggregateCache<A extends Aggregate> implements AggregateCache<A> {
  static final int DEFAULT_CACHE_SIZE = 100;

  @Getter private Class<A> aggregateType;
  private final Consumer<CacheBuilder<?, ?>> configurer;

  protected final AtomicReference<Cache<@NonNull UUID, @NonNull A>> cache = new AtomicReference<>();
  protected Subscription subscription;

  private void withCache(Consumer<Cache<@NonNull UUID, @NonNull A>> consumer) {
    Cache<@NonNull UUID, @NonNull A> c = cache.get();
    if (c != null) consumer.accept(c);
  }

  public void invalidate(UUID uuid) {
    withCache(c -> c.invalidate(uuid));
  }

  void invalidate(@NonNull Set<UUID> uuids) {
    withCache(c -> uuids.forEach(c::invalidate));
  }

  @Override
  public long size() {
    Cache<@NonNull UUID, @NonNull A> c = cache.get();
    if (c != null) return c.size();
    else return 0;
  }

  public void invalidateAll() {
    withCache(Cache::invalidateAll);
  }

  @Nullable
  @Override
  public A get(@NonNull UUID id) {
    Cache<@NonNull UUID, @NonNull A> c = cache.get();
    if (c != null) return c.getIfPresent(id);
    else return null;
  }

  @Override
  public void put(@NonNull UUID id, @NonNull A aggregate) {
    withCache(c -> c.put(id, aggregate));
  }

  // package private

  DefaultAggregateCache(
      @NonNull Class<A> aggregateType, @NonNull Consumer<CacheBuilder<?, ?>> configurer) {
    this.aggregateType = aggregateType;
    this.configurer = configurer;
  }

  protected void start(Factus factus, FactSpecProvider factSpecProvider) {
    Collection<FactSpec> factSpecs =
        factSpecProvider.forSnapshot(aggregateType).stream()
            .map(FactSpec::withoutAggIds)
            .collect(Collectors.toList());
    CacheBuilder<?, ?> builder = CacheBuilder.newBuilder();
    configurer.accept(builder);
    builder.softValues();
    // TODO
    cache.set((Cache<@NonNull UUID, @NonNull A>) builder.build());

    this.subscription =
        factus
            .factCast()
            .subscribe(
                SubscriptionRequestTO.from(SubscriptionRequest.follow(factSpecs).fromNowOn()),
                createFactObserver());
    /**/
    log.info("Done setting up aggregate cache: {}", this.getClass().getSimpleName());
  }

  protected void destroy() throws Exception {
    cache.set(null);
    if (subscription != null) {
      subscription.close();
    }
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
        if (uuids != null) invalidate(uuids);
      }

      @Override
      public void onError(@NonNull Throwable t) {
        withCache(Cache::invalidateAll);
        cache.set(null);
        log.error(
            "Error in subscription, falling back to uncached repository for type {}.",
            aggregateType,
            t);
      }
    };
  }
}
