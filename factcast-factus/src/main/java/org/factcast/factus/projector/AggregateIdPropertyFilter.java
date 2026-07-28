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
 * Therefore this filter cannot be expressed in the FactSpec and is instead evaluated right before
 * the handler is invoked: the {@code UUID} found at the configured property path of the event is
 * compared against the aggregate's id, and the handler is skipped on mismatch.
 */
@Value
class AggregateIdPropertyFilter {
  @NonNull String path;

  /**
   * the resolved chain of fields leading to the UUID property, made accessible. Fields are used
   * rather than getters so this is independent of the accessor style (JavaBeans or fluent Lombok
   * accessors) of the event.
   */
  @NonNull List<Field> fieldChain;

  /**
   * @return true if the event should be applied, i.e. the property value equals the aggregate id
   */
  boolean matches(@NonNull Projection projection, @NonNull Object[] parameters) {
    if (!(projection instanceof Aggregate aggregate)) {
      // discovery-time validation guarantees an Aggregate; be defensive and do not filter otherwise
      return true;
    }
    return Objects.equals(AggregateUtil.aggregateId(aggregate), extractFrom(findEvent(parameters)));
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

  @NonNull
  private static EventObject findEvent(@NonNull Object[] parameters) {
    for (Object p : parameters) {
      if (p instanceof EventObject e) {
        return e;
      }
    }
    throw new IllegalStateException(
        "No EventObject parameter present to evaluate @FilterByAggIdProperty");
  }
}
