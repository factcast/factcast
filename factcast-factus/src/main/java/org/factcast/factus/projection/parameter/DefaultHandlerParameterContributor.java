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
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.factcast.core.Fact;
import org.factcast.core.FactHeader;
import org.factcast.factus.event.EventObject;
import org.factcast.factus.event.EventSerializer;

@RequiredArgsConstructor
public class DefaultHandlerParameterContributor implements HandlerParameterContributor {
  final EventSerializer serializer;

  @Nullable
  @Override
  public HandlerParameterProvider providerFor(
      @NonNull Class<?> type, @NonNull Set<Annotation> annotations) {
    if (EventObject.class.isAssignableFrom(type)) {
      return (f, ctx) ->
          serializer.deserialize((Class<? extends EventObject>) type, f.jsonPayload());
    }

    if (Fact.class == type) {
      return (f, ctx) -> f;
    }

    if (FactHeader.class == type) {
      return (f, ctx) -> f.header();
    }

    if (UUID.class == type) {
      return (f, ctx) -> f.id();
    }

    return null;
  }
}
