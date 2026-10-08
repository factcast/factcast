/*
 * Copyright © 2017-2022 factcast.org
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
package org.factcast.store.registry.transformation.cache;

import static org.factcast.store.registry.metrics.RegistryMetrics.GAUGE.CACHE_BUFFER;

import com.google.common.annotations.VisibleForTesting;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.Fact;
import org.factcast.store.internal.PgFact;
import org.factcast.store.registry.metrics.RegistryMetrics;

@Slf4j
class CacheBuffer {

  @Getter(AccessLevel.PROTECTED)
  private final Map<TransformationCache.Key, PgFact> buffer =
      Collections.synchronizedMap(new HashMap<>());

  @Getter(AccessLevel.PROTECTED)
  private final AtomicLong bufferSizeMetric;

  public CacheBuffer(RegistryMetrics registryMetrics) {
    this.bufferSizeMetric = registryMetrics.gauge(CACHE_BUFFER, new AtomicLong(0));
  }

  PgFact get(@NonNull TransformationCache.Key key) {
    // we accept that we may get a null result here during flushing of the buffer
    return buffer.get(key);
  }

  void put(@NonNull TransformationCache.Key cacheKey, @NonNull PgFact f) {
    buffer.putIfAbsent(cacheKey, f);
  }

  int size() {
    return buffer.size();
  }

  /**
   * Copies the buffer content, clears it and passes a copy to the consumer. This allows for a
   * consistent view of the data during processing.
   *
   * @param consumer the consumer that will process the buffered data before being cleared.
   */
  void iterateSnapshotAndClear(@NonNull Consumer<Map<TransformationCache.Key, Fact>> consumer) {
    HashMap<TransformationCache.Key, Fact> flushing;
    synchronized (buffer) {
      bufferSizeMetric.set(buffer.size());
      flushing = new HashMap<>(buffer);
      buffer.clear();
    }
    try {
      consumer.accept(flushing);
    } catch (Exception e) {
      log.warn("While flushing cacheBuffer", e);
    }
  }

  @VisibleForTesting
  boolean containsKey(TransformationCache.Key key) {
    return buffer.containsKey(key);
  }
}
