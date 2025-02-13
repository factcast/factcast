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

import com.fasterxml.jackson.annotation.*;
import com.google.common.collect.Lists;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import lombok.*;
import lombok.experimental.Accessors;
import org.factcast.core.spec.FactSpec;

@Data
@SuppressWarnings("java:S1948")
@Accessors(fluent = false, chain = false)
@JsonIgnoreProperties("since")
public class FullQueryBean implements Serializable {
  public static final int DEFAULT_LIMIT = 50;
  private final long defaultFrom;

  private LocalDate since = LocalDate.now();
  private LocalDate until;

  @Max(1000)
  @Min(1)
  private Integer limit;

  @Min(0)
  @Max(1000)
  private Integer offset;

  @Valid private List<FactCriteria> criteria = Lists.newArrayList(new FactCriteria());

  // currently not possible to filter on more than one aggId via api
  @Min(0)
  private BigDecimal from;

  @Min(1)
  private BigDecimal to;

  FullQueryBean(long startingSerial) {
    defaultFrom = startingSerial;
    from = BigDecimal.valueOf(startingSerial);
  }

  @NonNull
  public List<FactSpec> createFactSpecs() {
    return criteria.stream().flatMap(FactCriteria::createFactSpecs).toList();
  }

  @SuppressWarnings("java:S2637") // settings ns to null is intended
  public void reset() {
    since = LocalDate.now();
    limit = null;
    offset = null;
    criteria = Lists.newArrayList(new FactCriteria());
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

  // renamed from get* to escape the property detection
  public long resolveFromOrZero() {
    return Optional.ofNullable(from)
        // turn into inclusive offset
        .map(o -> Math.max(0, o.longValueExact() - 1))
        .orElse(0L);
  }
}
