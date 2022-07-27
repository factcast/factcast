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

import java.util.*;

import javax.annotation.Nullable;

import org.factcast.core.Fact;

import com.google.common.annotations.VisibleForTesting;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;

class CacheBuffer {
  private final Object mutex = new Object() {};

  @Getter(AccessLevel.PROTECTED)
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
      if (factOrNull != null || !buffer.containsKey(cacheKey)) buffer.put(cacheKey, factOrNull);
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
