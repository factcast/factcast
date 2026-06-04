/*
 * Copyright © 2017-2023 factcast.org
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
package org.factcast.factus.projection.parameter;

import com.google.common.base.Predicates;
import jakarta.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.*;
import lombok.NonNull;
import org.factcast.core.*;
import org.factcast.factus.Meta;
import org.factcast.factus.event.*;
import org.factcast.factus.event.EventObject;
import org.factcast.factus.projection.Projection;

@SuppressWarnings({"OptionalIsPresent", "java:S1872"})
public class DefaultHandlerParameterContributor implements HandlerParameterContributor {

  @Nullable
  @Override
  public HandlerParameterProvider providerFor(
      @NonNull Class<?> type, @Nullable Type genericType, @NonNull Set<Annotation> annotations) {
    if (EventObject.class.isAssignableFrom(type)) {
      return (s, f, p) -> s.deserialize((Class<? extends EventObject>) type, f.jsonPayload());
    }

    if (Fact.class == type) {
      return new FactProvider();
    }

    if (FactHeader.class == type) {
      return new FactHeaderProvider();
    }

    if (UUID.class == type) {
      return new FactIdProvider();
    }

    if (FactStreamPosition.class == type) {
      return new FactStreamPositionProvider();
    }

    Optional<Meta> metaAnnotation =
        annotations.stream()
            .filter(Predicates.instanceOf(Meta.class))
            .map(Meta.class::cast)
            .findFirst();
    if (metaAnnotation.isPresent()) {
      return new MetaProvider(metaAnnotation.get(), type, genericType, annotations);
    }

    // fall through
    return null;
  }
}

class FactProvider implements HandlerParameterProvider {
  @Override
  public Object apply(
      @NonNull EventSerializer s, @NonNull Fact fact, @NonNull Projection projection) {
    return fact;
  }
}

class FactIdProvider implements HandlerParameterProvider {
  @Override
  public Object apply(
      @NonNull EventSerializer s, @NonNull Fact fact, @NonNull Projection projection) {
    return fact.id();
  }
}

class FactStreamPositionProvider implements HandlerParameterProvider {
  @Override
  public Object apply(
      @NonNull EventSerializer s, @NonNull Fact fact, @NonNull Projection projection) {
    return FactStreamPosition.from(fact);
  }
}

class FactHeaderProvider implements HandlerParameterProvider {
  @Override
  public Object apply(
      @NonNull EventSerializer s, @NonNull Fact fact, @NonNull Projection projection) {
    return fact.header();
  }
}

@SuppressWarnings({"java:S1872"})
class MetaProvider implements HandlerParameterProvider {
  private final String key;
  private final Class<?> targetType;

  public MetaProvider(
      Meta meta, Class<?> targetType, Type genericType, Set<Annotation> annotations) {
    this.targetType = targetType;

    key = meta.value();

    checkPreconditionsForAllowedTypes(targetType);
    checkPreconditionsForKey(key);
    checkPreconditionsForString(targetType, annotations);
    checkPreconditionsForOptional(targetType, genericType);
    checkPreconditionsForCollection(targetType, genericType);
  }

  private void checkPreconditionsForCollection(Class<?> targetType, Type genericType) {
    if (Iterable.class.isAssignableFrom(targetType)) {
      if (!(genericType instanceof ParameterizedType)) {
        throw new IllegalArgumentException(
            "Unparametrized Collection detected. It should be List<String> instead.");
      } else {
        if (((ParameterizedType) genericType).getActualTypeArguments()[0] != String.class) {
          throw new IllegalArgumentException(
              "Badly parametrized Collection detected. It should be List<String> instead.");
        }
      }
    }
  }

  private void checkPreconditionsForKey(String key) {
    if (key == null || key.trim().isEmpty()) {
      throw new IllegalArgumentException("@Meta must specify a valid key");
    }
  }

  private void checkPreconditionsForAllowedTypes(Class<?> targetType) {
    if (!(targetType == Optional.class
        || targetType == List.class
        || targetType == Collection.class
        || targetType == Iterable.class
        || targetType == String.class)) {
      throw new IllegalArgumentException(
          "Only String, Optional<String> or List<String> types for @Meta annotated Parameters are allowed");
    }
  }

  private static void checkPreconditionsForString(
      Class<?> targetType, Set<Annotation> annotations) {
    if (targetType == String.class
        && annotations.stream()
            .noneMatch(a -> "Nullable".equals(a.annotationType().getSimpleName()))) {
      throw new IllegalArgumentException(
          "Parameters of type String declared with @Meta must also be annotated with @Nullable. You could also change it to Optional<String>.");
    }
  }

  private static void checkPreconditionsForOptional(Class<?> targetType, Type genericType) {
    if (targetType == Optional.class) {
      if (!(genericType instanceof ParameterizedType)) {
        throw new IllegalArgumentException(
            "Unparametrized Optional detected. It should be Optional<String> instead.");
      } else {
        if (((ParameterizedType) genericType).getActualTypeArguments()[0] != String.class) {
          throw new IllegalArgumentException(
              "Badly parametrized Optional detected. It should be Optional<String> instead.");
        }
      }
    }
  }

  @Override
  public Object apply(
      @NonNull EventSerializer s, @NonNull Fact fact, @NonNull Projection projection) {
    String value = fact.header().meta().getFirst(key);
    if (targetType == Optional.class) {
      return Optional.ofNullable(value);
    }
    if (isList(targetType) || isIterable(targetType)) {
      return fact.header().meta().getAll(key);
    } else {
      return value;
    }
  }

  private boolean isList(Class<?> targetType) {
    return targetType.isAssignableFrom(List.class);
  }

  private boolean isIterable(Class<?> targetType) {
    return targetType.isAssignableFrom(Iterable.class);
  }
}
