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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.factcast.factus.projection.SnapshotProjection;
import org.factcast.factus.serializer.JacksonSnapshotSerializer;
import org.factcast.factus.serializer.SnapshotSerializer;

@Slf4j
public class SnapshotSerializerSupplier {

  @NonNull private final SnapshotSerializer defaultSerializer;
  @NonNull private final SnapshotSerializerFactory factory;

  private final Map<Class<? extends SnapshotSerializer>, SnapshotSerializer> serializers =
      new ConcurrentHashMap<>();
  private final Map<Class<? extends SnapshotProjection>, SnapshotSerializer> cache =
      new ConcurrentHashMap<>();

  public SnapshotSerializerSupplier(@NonNull SnapshotSerializer defaultSerializer) {
    this(defaultSerializer, new SnapshotSerializerFactory.Default());
  }

  public SnapshotSerializerSupplier(
      @NonNull SnapshotSerializer defaultSerializer, @NonNull SnapshotSerializerFactory factory) {
    this.defaultSerializer = defaultSerializer;
    this.factory = factory;
    if (!(defaultSerializer instanceof JacksonSnapshotSerializer)) {
      log.info(
          "Using {} as a default SnapshotSerializer", defaultSerializer.getClass().getSimpleName());
    }
  }

  public org.factcast.factus.serializer.SnapshotSerializer retrieveSerializer(
      @NonNull Class<? extends SnapshotProjection> aClass) {
    return cache.computeIfAbsent(
        aClass,
        clazz -> {
          SerializeUsing classAnnotation = aClass.getAnnotation(SerializeUsing.class);
          if (classAnnotation == null) {
            return defaultSerializer;
          } else {
            Class<? extends SnapshotSerializer> ser = classAnnotation.value();
            return serializers.computeIfAbsent(ser, factory::create);
          }
        });
  }
}
