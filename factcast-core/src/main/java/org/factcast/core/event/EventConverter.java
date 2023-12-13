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
package org.factcast.core.event;

import java.util.*;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.factcast.core.Fact;
import org.factcast.factus.event.EventObject;
import org.factcast.factus.event.EventSerializer;

@RequiredArgsConstructor
public class EventConverter {
  @NonNull final EventSerializer ser;

  public Fact toFact(@NonNull EventObject p) {
    return toFact(p, UUID.randomUUID());
  }

  public Fact toFact(@NonNull EventObject p, @NonNull UUID factId) {
    // keep compatibility to older code that expects this to throw an IllegalArgException instead of
    // an NPE, when a key in the meta map is null
    if (p.additionalMetaMap().keySet().stream().anyMatch(Objects::isNull))
      throw new IllegalArgumentException("Meta-Keys cannot be null");

    return Fact.buildFrom(p).using(ser).id(factId).build();
  }
}
