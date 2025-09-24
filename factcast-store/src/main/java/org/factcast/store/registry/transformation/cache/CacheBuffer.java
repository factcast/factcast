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
import static org.factcast.store.registry.metrics.RegistryMetrics.GAUGE.CACHE_FLUSHING_BUFFER;

import com.google.common.annotations.VisibleForTesting;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import org.factcast.core.Fact;
import org.factcast.store.internal.PgFact;
import org.factcast.store.registry.metrics.RegistryMetrics;

class CacheBuffer {
  private final Object mutex = new Object() {};

  // Currently we allow null values in the buffer, reason why we can't use ConcurrentHashMap.
  @Getter(AccessLevel.PROTECTED)
  private final Map<TransformationCache.Key, PgFact> buffer = new HashMap<>();

  @Getter(AccessLevel.PROTECTED)
  private final Map<TransformationCache.Key, PgFact> flushingBuffer = new HashMap<>();

  @Getter(AccessLevel.PROTECTED)
  private final AtomicLong bufferSizeMetric;

  @Getter(AccessLevel.PROTECTED)
  private final AtomicLong flushingBufferSizeMetric;

  public CacheBuffer(RegistryMetrics registryMetrics) {
    this.bufferSizeMetric = registryMetrics.gauge(CACHE_BUFFER, new AtomicLong(0));
    this.flushingBufferSizeMetric = registryMetrics.gauge(CACHE_FLUSHING_BUFFER, new AtomicLong(0));
  }

  PgFact get(@NonNull TransformationCache.Key key) {
    synchronized (mutex) {
      return Optional.ofNullable(buffer.get(key)).orElse(flushingBuffer.get(key));
    }
  }

  void put(@NonNull TransformationCache.Key cacheKey, @Nullable PgFact factOrNull) {
    synchronized (mutex) {
      // do not override potential transformations
      // cannot use computeIfAbsent here as null values are not allowed.
      if (factOrNull != null || !buffer.containsKey(cacheKey)) {
        buffer.put(cacheKey, factOrNull);
      }
    }
  }

  int size() {
    synchronized (mutex) {
      return buffer.size();
    }
  }

  /**
   * Copies the buffer content, clears it and passes a copy to the consumer. This allows for a
   * consistent view of the data during processing.
   *
   * @param consumer the consumer that will process the buffered data before being cleared.
   */
  void clearAfter(@NonNull Consumer<Map<TransformationCache.Key, Fact>> consumer) {
    // the consumer is not synchronized, in order to allow concurrent access to the buffer
    // while it's processing the data.
    try {
      beforeClearConsumer();
      consumer.accept(Collections.unmodifiableMap(flushingBuffer));
    } finally {
      afterClearConsumer();
    }
  }

  void putAllNull(Collection<TransformationCache.Key> keys) {
    synchronized (mutex) {
      keys.forEach(k -> put(k, null));
    }
  }

  @VisibleForTesting
  boolean containsKey(TransformationCache.Key key) {
    synchronized (mutex) {
      return buffer.containsKey(key);
    }
  }

  private void beforeClearConsumer() {
    synchronized (mutex) {
      bufferSizeMetric.set(buffer.size());
      flushingBuffer.putAll(buffer);
      buffer.clear();
    }
  }

  private void afterClearConsumer() {
    synchronized (mutex) {
      flushingBufferSizeMetric.set(flushingBuffer.size());
      flushingBuffer.clear();
    }
  }
}
