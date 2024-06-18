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
package org.factcast.server.ui.full;

import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.Data;
import lombok.experimental.Accessors;
import org.factcast.core.spec.FactSpec;

@Data
@Accessors(fluent = false, chain = false)
public class FactCriteria implements Serializable {

  @NotNull private String ns;

  private Set<String> type = null;
  private UUID aggId = null;
  private List<MetaTuple> meta = new LinkedList<>();

  public Stream<FactSpec> createFactSpecs() {

    if (type != null) {
      return type.stream()
          .map(
              t -> {
                FactSpec fs = FactSpec.ns(ns).type(t);

                if (aggId != null) {
                  fs.aggId(aggId);
                }

                if (!meta.isEmpty()) {
                  meta.forEach(m -> fs.meta(m.getKey(), m.getValue()));
                }
                return fs;
              });
    } else if (ns == null) {
      throw new IllegalArgumentException("You need at least a namespace to query");
    } else {
      FactSpec fs = FactSpec.ns(ns);
      if (aggId != null) {
        fs.aggId(aggId);
      }
      if (!meta.isEmpty()) {
        meta.forEach(m -> fs.meta(m.getKey(), m.getValue()));
      }
      return Stream.of(fs);
    }
  }

  /** must match for referential equality only */
  @Override
  public boolean equals(Object o) {
    return this == o;
  }

  /** see equals(Object) */
  @Override
  public int hashCode() {
    return 1;
  }
}
