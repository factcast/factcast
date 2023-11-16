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

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import lombok.Data;
import lombok.NonNull;
import lombok.experimental.Accessors;
import org.factcast.core.spec.FactSpec;

@Data
@SuppressWarnings("java:S1948")
@Accessors(fluent = false, chain = false)
public class FullQueryBean implements Serializable {
  public static final int DEFAULT_LIMIT = 50;
  private final long defaultFrom;
  private LocalDate since = LocalDate.now();

  @Max(1000)
  private Integer limit = null;

  @Min(0)
  private Integer offset = null;

  @NotNull private String ns;

  private Set<String> type = null;
  // currently not possible to filter on more than one aggId via api
  private UUID aggId = null;
  private List<MetaTuple> meta = new LinkedList<>();
  private BigDecimal from = null;

  FullQueryBean(long startingSerial) {
    defaultFrom = startingSerial;
    from = BigDecimal.valueOf(startingSerial);
  }

  @NonNull
  public List<FactSpec> createFactSpecs() {
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
              })
          .toList();
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
      return Collections.singletonList(fs);
    }
  }

  @SuppressWarnings("java:S2637") // settings ns to null is intended
  public void reset() {
    since = LocalDate.now();
    limit = null;
    offset = null;

    ns = null;
    type = null;
    aggId = null;
    meta.clear();
    from = null;
    from = BigDecimal.valueOf(defaultFrom);
  }

  @JsonIgnore
  public int getOffsetOrDefault() {
    return Optional.ofNullable(offset).orElse(0);
  }

  @JsonIgnore
  public int getLimitOrDefault() {
    return Optional.ofNullable(limit).orElse(FullQueryBean.DEFAULT_LIMIT);
  }
}
