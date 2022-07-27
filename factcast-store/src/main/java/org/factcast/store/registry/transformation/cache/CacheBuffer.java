package org.factcast.store.registry.transformation.cache;

import java.util.*;

import javax.annotation.Nullable;

import org.factcast.core.Fact;

import com.google.common.annotations.VisibleForTesting;

import lombok.NonNull;

class CacheBuffer {
  private final Object mutex = new Object() {};
  private final Map<TransformationCache.Key, Fact> buffer = new HashMap<>();

  Fact get(@NonNull TransformationCache.Key key) {
    synchronized (mutex) {
      return buffer.get(key);
    }
  }

  void put(@NonNull TransformationCache.Key cacheKey, @Nullable Fact factOrNull) {
    synchronized (mutex) {
      // do not override potential transformations
      // cannot use computeIfAbsent here as null values are not allowed.
      if (!buffer.containsKey(cacheKey)) buffer.put(cacheKey, factOrNull);
    }
  }

  int size() {
    synchronized (mutex) {
      return buffer.size();
    }
  }

  Map<TransformationCache.Key, Fact> clear() {
    synchronized (mutex) {
      Map<TransformationCache.Key, Fact> ret = Collections.unmodifiableMap(new HashMap<>(buffer));
      buffer.clear();
      return ret;
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
}
