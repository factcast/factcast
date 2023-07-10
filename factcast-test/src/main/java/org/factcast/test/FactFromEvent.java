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
package org.factcast.test;

import java.lang.annotation.Annotation;
import java.util.UUID;
import org.factcast.core.Fact;
import org.factcast.factus.event.EventObject;
import org.factcast.factus.event.Specification;
import org.jetbrains.annotations.NotNull;

public class FactFromEvent {

  public static Fact.Builder factFromEvent(@NotNull EventObject event) {
    return factFromEvent(event, 0);
  }

  public static Fact.Builder factFromEvent(@NotNull EventObject event, long serial) {
    return factFromEvent(event, serial, UUID.randomUUID());
  }

  public static Fact.Builder factFromEvent(@NotNull EventObject event, long serial, UUID id) {
    Annotation[] annotations = event.getClass().getAnnotations();
    Specification specs = null;
    int i = 0;
    while (specs == null && i < annotations.length) {
      if (annotations[i] instanceof Specification) {
        specs = (Specification) annotations[i];
      }
      i++;
    }

    if (specs == null) {
      throw new IllegalStateException("invalid event object");
    } else {
      return Fact.builder()
          .type(specs.type())
          .ns(specs.ns())
          .version(specs.version())
          .id(id)
          .meta("_ser", String.valueOf(serial));
    }
  }
}
