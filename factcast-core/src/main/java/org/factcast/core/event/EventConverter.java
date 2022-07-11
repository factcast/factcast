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
import org.factcast.core.Fact.Builder;
import org.factcast.core.spec.FactSpecCoordinates;
import org.factcast.factus.event.EventObject;
import org.factcast.factus.event.EventSerializer;

@RequiredArgsConstructor
public class EventConverter {
  @NonNull final EventSerializer ser;

  public Fact toFact(@NonNull EventObject p) {
    return toFact(p, UUID.randomUUID());
  }

  public Fact toFact(@NonNull EventObject p, @NonNull UUID factId) {
    FactSpecCoordinates spec = FactSpecCoordinates.from(p.getClass());

    Builder b = Fact.builder();
    b.id(factId);
    b.ns(spec.ns());
    String type = spec.type();
    if (type == null || type.trim().isEmpty()) {
      type = p.getClass().getSimpleName();
    }
    b.type(type);
    int version = spec.version();
    if (version > 0) // 0 is not allowed on publishing
    {
      b.version(version);
    }

    p.aggregateIds().forEach(b::aggId);

    p.additionalMetaMap()
        .forEach(
            (key, value) -> {
              if (key == null) {
                throw new IllegalArgumentException(
                    "Keys of additional fact headers must not be null ('"
                        + key
                        + "':'"
                        + value
                        + "')");
              }
              b.meta(key, value);
            });

    return b.build(ser.serialize(p));
  }
}
