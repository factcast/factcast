/*
 * Copyright © 2017-2020 factcast.org
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
package org.factcast.factus.snapshot;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.util.Map;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.factcast.factus.projection.SnapshotProjection;
import org.factcast.factus.serializer.SnapshotSerializer;

@Slf4j
public class SnapshotSerializerSelector {

  @NonNull private final SnapshotSerializer defaultSerializer;
  @NonNull private final SnapshotSerializerSupplier factory;

  private final Map<Class<?>, SnapshotSerializer> cache;

  public SnapshotSerializerSelector(
      @NonNull SnapshotSerializer defaultSerializer, @NonNull SnapshotSerializerSupplier factory) {
    this.defaultSerializer = defaultSerializer;
    this.factory = factory;
    log.info(
        "Using {} as a default SnapshotSerializer", defaultSerializer.getClass().getSimpleName());

    // as the keys are classes that might be extended at runtime, we prevent a potential memleak
    // with weakKeys
    Cache<Class<?>, SnapshotSerializer> theCache = CacheBuilder.newBuilder().weakKeys().build();
    cache = theCache.asMap();
  }

  @NonNull
  public SnapshotSerializer selectSeralizerFor(
      @NonNull Class<? extends SnapshotProjection> aClass) {
    return cache.computeIfAbsent(
        aClass,
        clazz -> {
          SerializeUsing classAnnotation = aClass.getAnnotation(SerializeUsing.class);
          if (classAnnotation == null) {
            return defaultSerializer;
          } else {
            return factory.get(classAnnotation.value());
          }
        });
  }
}
