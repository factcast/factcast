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
package org.factcast.factus.projector;

import java.util.*;
import lombok.*;
import org.factcast.factus.*;
import org.factcast.factus.event.*;
import org.factcast.factus.event.EventObject;
import org.factcast.factus.projection.LocalManagedProjection;

class ComplexProjection extends LocalManagedProjection {

  @Getter private ComplexEvent recordedEvent;

  @Getter private ComplexEvent2 recordedEvent2;

  class Nested {

    @Handler
    void apply(ComplexEvent foo) {
      recordedEvent = foo;
    }

    @Handler
    void apply2(ComplexEvent2 foo) {
      recordedEvent2 = foo;
    }
  }
}

class ComplexProjectionWithCatchall extends ComplexProjection {
  @Getter private LocalEvent recordedEvent3;

  @HandlerFor(ns = "*", type = "*")
  void wildarc(LocalEvent foo) {
    recordedEvent3 = foo;
  }

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  @Specification(ns = "neverMind")
  public static class LocalEvent implements EventObject {

    private String code;

    @Override
    public Set<UUID> aggregateIds() {
      return new HashSet<>();
    }
  }
}
