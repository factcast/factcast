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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.factcast.server.ui.views.filter.AbstractFilterBean;
import org.factcast.server.ui.views.filter.FactCriteria;
import org.factcast.server.ui.views.filter.FilterBean;

@Data
@SuppressWarnings("java:S1948")
@Accessors(fluent = false, chain = false)
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties("since")
public class ReportFilterBean extends AbstractFilterBean implements FilterBean, Serializable {
  private final long defaultFrom;

  public ReportFilterBean(long startingSerial) {
    super(startingSerial);
    defaultFrom = startingSerial;
  }

  @JsonCreator
  public ReportFilterBean(
      @JsonProperty("defaultFrom") long defaultFrom,
      @JsonProperty("from") BigDecimal from,
      @JsonProperty("criteria") List<FactCriteria> criteria) {
    super(from, criteria);
    this.defaultFrom = defaultFrom;
  }

  @Override
  public void reset() {
    super.reset(BigDecimal.valueOf(defaultFrom));
  }
}
