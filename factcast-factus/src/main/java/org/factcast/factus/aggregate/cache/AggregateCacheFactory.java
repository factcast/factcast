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

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.factcast.factus.Factus;
import org.factcast.factus.projection.Aggregate;
import org.factcast.factus.projector.FactSpecProvider;
import org.springframework.beans.factory.DisposableBean;

@Slf4j
@RequiredArgsConstructor
public class AggregateCacheFactory implements DisposableBean {
  @NonNull private final Factus factus;
  @NonNull private final FactSpecProvider specProvider;
  private List<DefaultAggregateCache<?>> caches = new CopyOnWriteArrayList<>();

  @NonNull
  public <T extends Aggregate> AggregateCache<T> create(
      @NonNull Class<T> aggregateClass, int cacheSize) {
    DefaultAggregateCache<T> c =
        new DefaultAggregateCache<>(
            aggregateClass, cacheBuilder -> cacheBuilder.maximumSize(cacheSize));
    caches.add(c);
    c.start(factus, specProvider);
    return c;
  }

  @NonNull
  public <T extends Aggregate> AggregateCache<T> create(@NonNull Class<T> aggregateClass) {
    return create(aggregateClass, DefaultAggregateCache.DEFAULT_CACHE_SIZE);
  }

  @Override
  public void destroy() throws Exception {
    for (DefaultAggregateCache<?> c : caches) {
      try {
        c.destroy();
      } catch (Exception e) {
        log.warn("While destroying {}", c, e);
      }
    }
  }
}
