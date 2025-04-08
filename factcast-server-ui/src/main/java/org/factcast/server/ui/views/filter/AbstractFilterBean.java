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
package org.factcast.server.ui.views.filter;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.collect.Lists;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.experimental.Accessors;

@Data
@Accessors(fluent = false, chain = false)
@NoArgsConstructor
@JsonIgnoreProperties({"since", "until"})
public abstract class AbstractFilterBean implements FilterBean {
  private LocalDate since = LocalDate.now();
  private LocalDate until;

  @Valid private List<FactCriteria> criteria = Lists.newArrayList(new FactCriteria());

  // currently not possible to filter on more than one aggId via api
  @Min(0)
  private BigDecimal from;

  @Min(1)
  private BigDecimal to;

  protected AbstractFilterBean(long startingSerial) {
    this.from = BigDecimal.valueOf(startingSerial);
  }

  protected AbstractFilterBean(BigDecimal from, List<FactCriteria> criteria) {
    this.from = from;
    this.criteria = criteria;
  }

  @SuppressWarnings("java:S2637") // settings ns to null is intended
  public void reset(@NonNull BigDecimal fromDefault) {
    since = LocalDate.now();
    criteria = Lists.newArrayList(new FactCriteria());
    this.from = fromDefault;
    to = null;
  }
}
