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
package org.factcast.server.ui.report;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.collect.Lists;
import jakarta.validation.Valid;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.Data;
import lombok.experimental.Accessors;
import org.factcast.server.ui.views.filter.FactCriteria;
import org.factcast.server.ui.views.filter.FilterBean;

@Data
@SuppressWarnings("java:S1948")
@Accessors(fluent = false, chain = false)
@JsonIgnoreProperties("since")
public class ReportFilterBean implements FilterBean, Serializable {
  private final long defaultFrom;

  private LocalDate since = LocalDate.now();

  // currently not possible to filter on more than one aggId via api
  private BigDecimal from = null;

  @Valid private List<FactCriteria> criteria = Lists.newArrayList(new FactCriteria());

  ReportFilterBean(long startingSerial) {
    defaultFrom = startingSerial;
    from = BigDecimal.valueOf(startingSerial);
  }

  @Override
  @SuppressWarnings("java:S2637") // settings ns to null is intended
  public void reset() {
    since = LocalDate.now();
    criteria.clear();
    criteria.add(new FactCriteria());
    from = BigDecimal.valueOf(defaultFrom);
  }

  // TODO find out how to disable limit
  @Override
  public Integer getLimit() {
    return 10;
  }

  @Override
  public void setLimit(Integer limit) {}

  @Override
  public Integer getOffset() {
    return 0;
  }

  @Override
  public void setOffset(Integer offset) {}

  @Override
  @JsonIgnore
  public int getOffsetOrDefault() {
    return 0;
  }

  // TODO find out how to disable limit
  @Override
  @JsonIgnore
  public int getLimitOrDefault() {
    return 10;
  }
}