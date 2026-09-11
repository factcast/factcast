/*
 * Copyright © 2017-2025 factcast.org
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
package org.factcast.factus.projector;

import jakarta.annotation.Nullable;
import java.lang.reflect.*;
import lombok.NonNull;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.Fact;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.util.ExceptionHelper;
import org.factcast.factus.event.EventSerializer;
import org.factcast.factus.projection.Projection;
import org.factcast.factus.projection.parameter.HandlerParameterTransformer;

@Slf4j
@Value
class Dispatcher {

  @NonNull Method dispatchMethod;
  @NonNull HandlerParameterTransformer transformer;

  @NonNull ProjectorImpl.TargetObjectResolver objectResolver;

  @NonNull FactSpec spec;

  /** client-side filter for @FilterByAggIdProperty, or null if the handler is not annotated. */
  @Nullable AggregateIdPropertyFilter aggIdPropertyFilter;

  void invoke(
      @NonNull EventSerializer deserializer, @NonNull Projection projection, @NonNull Fact f) {
    // choose the target object (nested)
    Object targetObject = objectResolver.apply(projection);
    // create actual parameters
    Object[] parameters = transformer.apply(deserializer, f, projection);
    // client-side @FilterByAggIdProperty: skip facts whose property does not match the aggregate id
    if (aggIdPropertyFilter != null && !aggIdPropertyFilter.matches(projection, parameters)) {
      log.trace("Skipping fact {} due to @FilterByAggIdProperty mismatch", f.id());
      return;
    }
    // fire
    try {
      dispatchMethod.invoke(targetObject, parameters);
    } catch (IllegalAccessException e) {
      throw ExceptionHelper.toRuntime(e);
    } catch (InvocationTargetException e) {
      // unwrap
      throw ExceptionHelper.toRuntime(e.getCause());
    }
  }
}
