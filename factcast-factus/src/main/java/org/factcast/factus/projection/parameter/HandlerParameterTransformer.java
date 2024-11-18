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

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Collectors;
import lombok.NonNull;
import org.factcast.core.Fact;
import org.factcast.factus.event.EventSerializer;
import org.factcast.factus.projection.Projection;

/**
 * facilitates {@link HandlerParameterContributor}s to create parameter array for a handler method
 */
public interface HandlerParameterTransformer
    extends TriFunction<@NonNull EventSerializer, @NonNull Fact, @NonNull Projection, Object[]> {
  @NonNull
  static HandlerParameterTransformer forCalling(
      @NonNull Method m, HandlerParameterContributors handlerParameterContributors) {
    Class<?>[] parameterTypes = m.getParameterTypes();
    Type[] genericParameterTypes = m.getGenericParameterTypes();
    List<Set<Annotation>> annotations =
        Arrays.stream(m.getParameterAnnotations())
            .map(aa -> new HashSet<>(Arrays.asList(aa)))
            .collect(Collectors.toList());
    HandlerParameterProvider[] providers = new HandlerParameterProvider[parameterTypes.length];

    // select providers according to type & annotations
    for (int i = 0; i < parameterTypes.length; i++) {
      Class<?> type = parameterTypes[i];
      Type genericType = genericParameterTypes[i];
      final int index = i;
      providers[i] =
          chooseProvider(handlerParameterContributors, type, genericType, annotations.get(i))
              .orElseThrow(
                  () ->
                      new IllegalArgumentException(
                          "Cannot find resolver for parameter "
                              + index
                              + " of signature "
                              + m.toString()));
    }

    // executed per call:
    return (serializer, fact, p) -> {
      Object[] parameters = new Object[providers.length];

      for (int i = 0; i < providers.length; i++) {
        // create parameter for this call
        parameters[i] = providers[i].apply(serializer, fact, p);
      }
      return parameters;
    };
  }

  @NonNull
  static Optional<HandlerParameterProvider> chooseProvider(
      HandlerParameterContributors contributors,
      Class<?> type,
      Type genericType,
      Set<Annotation> annotation) {
    return contributors.stream()
        .map(c -> c.providerFor(type, genericType, annotation))
        .filter(Objects::nonNull)
        .findFirst();
  }
}
