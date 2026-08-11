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
import java.util.*;
import lombok.NonNull;
import lombok.Value;
import org.factcast.core.util.ExceptionHelper;
import org.factcast.factus.event.EventObject;
import org.factcast.factus.projection.Aggregate;
import org.factcast.factus.projection.AggregateUtil;
import org.factcast.factus.projection.Projection;

/**
 * Client-side filter backing {@link org.factcast.factus.FilterByAggIdProperty}.
 *
 * <p>Unlike the static filter annotations (which are turned into {@code FactSpec} entries and
 * evaluated server-side), the aggregate id to match against is only known per fetched instance.
 * Expressing it server-side would require a per-instance {@code FactSpec.filterScript}, which is
 * store-specific; to keep the feature portable it is deliberately evaluated client-side instead,
 * right before the handler is invoked: non-matching facts are still transferred and deserialized,
 * but the handler is skipped when the {@code UUID} found at the configured property path of the
 * event does not equal the aggregate's id.
 */
@Value
class AggregateIdPropertyFilter {
  /**
   * the resolved chain of fields leading to the UUID property, made accessible. Fields are used
   * rather than getters so this is independent of the accessor style (JavaBeans or fluent Lombok
   * accessors) of the event.
   */
  @NonNull List<Field> fieldChain;

  /** index of the handler's {@link EventObject} parameter, determined at discovery time. */
  int eventParameterIndex;

  /**
   * @return true if the event should be applied, i.e. the property value equals the aggregate id
   */
  boolean matches(@NonNull Projection projection, @NonNull Object[] parameters) {
    if (!(projection instanceof Aggregate aggregate)) {
      throw new IllegalStateException(
          "@FilterByAggIdProperty is only supported on Aggregate projections, but was evaluated"
              + " against "
              + projection.getClass().getName());
    }
    return Objects.equals(
        AggregateUtil.aggregateId(aggregate),
        extractFrom((EventObject) parameters[eventParameterIndex]));
  }

  @Nullable
  private UUID extractFrom(@NonNull EventObject event) {
    Object current = event;
    for (Field field : fieldChain) {
      if (current == null) {
        return null;
      }
      try {
        current = field.get(current);
      } catch (IllegalAccessException e) {
        throw ExceptionHelper.toRuntime(e);
      }
    }
    return (UUID) current;
  }
}
