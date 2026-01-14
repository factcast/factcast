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
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.*;
import org.factcast.core.spec.*;
import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.SubscriptionRequest;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.core.subscription.observer.FactObserver;
import org.factcast.factus.Factus;
import org.factcast.factus.projection.*;
import org.factcast.factus.projector.FactSpecProvider;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.GenericTypeResolver;

@Slf4j
@RequiredArgsConstructor
public abstract class AbstractAggregateCache<A extends Aggregate>
    implements AggregateCache<A>, DisposableBean, SmartInitializingSingleton {

  private static final int DEFAULT_CACHE_SIZE = 100;
  @Getter private Class<A> aggregateType;

  protected final @NonNull Factus factus;
  protected final @NonNull FactSpecProvider factSpecProvider;

  protected final AtomicBoolean enabled = new AtomicBoolean(true);

  protected Cache<@NonNull UUID, @NonNull A> cache;
  protected Subscription subscription;

  @Nullable
  @Override
  public A get(@NonNull UUID id) {
    if (enabled.get()) {
      return cache.getIfPresent(id);
    } else {
      return null;
    }
  }

  @Override
  public void put(@NonNull UUID id, @NonNull A aggregate) {
    if (enabled.get()) {
      cache.put(id, aggregate);
    }
  }

  /**
   * if you consider overriding, make sure to call super.afterSingletonsInstantiated first, or - if
   * you have a very good reason to - reimplement parts of the initialization yourself.
   *
   * <p>Even though the initialization is crucial, we did not want to make it final, as this makes
   * it harder/awkward to extend in case you want a hook for initializiation,
   */
  @SuppressWarnings("unchecked")
  @Override
  public void afterSingletonsInstantiated() {
    aggregateType =
        (Class<A>)
            GenericTypeResolver.resolveTypeArgument(getClass(), AbstractAggregateCache.class);

    cache =
        configure(CacheBuilder.newBuilder())
            // non negotiable:
            .softValues()
            .<@NonNull UUID, @NonNull A>build();

    @NonNull
    Collection<FactSpec> factSpecs =
        factSpecProvider.forSnapshot(aggregateType).stream()
            .map(FactSpec::withoutAggIds)
            .collect(Collectors.toList());

    this.subscription = factus.factCast().subscribe(createRequest(factSpecs), createFactObserver());
    log.info("Done setting up aggregate cache: {}", this.getClass().getSimpleName());
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

  @VisibleForTesting
  @NonNull
  FactObserver createFactObserver() {
    return new FactObserver() {
      @Override
      public void onNext(@NonNull Fact f) {
        Set<UUID> uuids = f.header().aggIds();
        // yes, depending on the self-referencing issue and the implementation of
        // Projection::process, this might invalidate more than strictly necessary.
        if (uuids != null) {
          uuids.forEach(AbstractAggregateCache.this::invalidate);
        }
      }

      @Override
      public void onError(@NonNull Throwable t) {
        enabled.set(false);
        cache.invalidateAll();
        log.error("Error in subscription, falling back to uncached repository.", t);
      }
    };
  }

  /** Hook especially useful for testing. */
  protected void invalidate(UUID uuid) {
    cache.invalidate(uuid);
  }

  @VisibleForTesting
  @NonNull
  SubscriptionRequestTO createRequest(@NonNull Collection<FactSpec> factSpecs) {
    return SubscriptionRequestTO.from(SubscriptionRequest.follow(factSpecs).fromNowOn());
  }
}
