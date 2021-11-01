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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.factcast.factus.projection.SnapshotProjection;
import org.factcast.factus.serializer.JacksonSnapshotSerializer;
import org.factcast.factus.serializer.SnapshotSerializer;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SnapshotSerializerSupplier {

  @NonNull private final SnapshotSerializer defaultSerializer;
  @NonNull private final List<SnapshotSerializer> allSnapshotSerializers;

  private final Map<Class<? extends SnapshotSerializer>, SnapshotSerializer> serializers =
      new ConcurrentHashMap<>();
  private final Map<Class<? extends SnapshotProjection>, SnapshotSerializer> cache =
      new ConcurrentHashMap<>();

  public SnapshotSerializerSupplier(
      @NonNull SnapshotSerializer defaultSerializer,
      @NonNull List<SnapshotSerializer> allSnapshotSerializers) {
    this.defaultSerializer = defaultSerializer;
    this.allSnapshotSerializers = allSnapshotSerializers;
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
            return findFirstApplicableSerializer(classAnnotation.value(), aClass);
          }
        });
  }

  private SnapshotSerializer findFirstApplicableSerializer(
      Class<? extends SnapshotSerializer>[] candidates,
      Class<? extends SnapshotProjection> aClass) {

    if (candidates == null || candidates.length == 0) {
      throw new SerializerInstantiationException(
          "@SerializeUsing used with empty lists of serializers on " + aClass + ".");
    }

    for (Class<? extends SnapshotSerializer> c : candidates) {

      // first check if we have given serializer as bean (or constructed it previously)
      SnapshotSerializer beanOrConstructedInstance =
          serializers.computeIfAbsent(
              c,
              key ->
                  allSnapshotSerializers.stream()
                      .filter(s -> s.getClass().equals(key))
                      .findFirst()
                      .orElse(null));

      if (beanOrConstructedInstance != null) {
        return beanOrConstructedInstance;
      }

      // not a bean? Check if it has a public default constructor
      try {
        if (Modifier.isPublic(c.getDeclaredConstructor().getModifiers())) {

          // looks good, try to construct and put into serializers cache
          SnapshotSerializer serializer =
              serializers.computeIfAbsent(c, SnapshotSerializerSupplier::instantiate);

          if (serializer != null) {
            return serializer;
          }

          // if was null, try next candidate
        }
      } catch (NoSuchMethodException e) {
        // not possible to construct, try next candidate
      }

      log.warn(
          "SnapshotSerializer {} was listed in @SerializeUsing(...) on class {}, but neither found a bean of this type, "
              + "nor was it possible to construct an instance of it using a public default constructor.",
          c,
          aClass);
    }

    String serializerNames =
        Arrays.stream(candidates).map(Class::toString).collect(Collectors.joining(", "));

    // no candidate found, neither bean nor public default constructor, throw exception
    throw new SerializerInstantiationException(
        "None of the given serializers ("
            + serializerNames
            + ") listed in @SerializeUsing(...) on class "
            + aClass
            + " were found as bean or could be instantiated.");
  }

  private static <C> C instantiate(Class<C> clazz) {
    try {
      return clazz.getDeclaredConstructor().newInstance();

    } catch (InstantiationException
        | IllegalAccessException
        | InvocationTargetException
        | NoSuchMethodException e) {
      // since we have checked if there is a public non-default constructor previously,
      // we will rarely end up here. But if we do, log a warning
      log.warn("Not able to create an instance of serializer " + clazz + ".", e);
      return null;
    }
  }
}
