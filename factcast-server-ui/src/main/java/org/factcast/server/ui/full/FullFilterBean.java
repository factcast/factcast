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
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Optional;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.factcast.server.ui.views.filter.AbstractFilterBean;

@Data
@SuppressWarnings("java:S1948")
@Accessors(fluent = false, chain = false)
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties({"since", "until"})
public class FullFilterBean extends AbstractFilterBean implements Serializable {
  private final long defaultFrom;

  public static final int DEFAULT_LIMIT = 50;

  @Max(1000)
  @Min(1)
  private Integer limit;

  @Min(0)
  @Max(1000)
  private Integer offset;

  public FullFilterBean(long startingSerial) {
    super(startingSerial);
    this.defaultFrom = startingSerial;
  }

  @Override
  public void reset() {
    super.reset(BigDecimal.valueOf(defaultFrom));
    limit = null;
    offset = null;
  }

  @JsonIgnore
  public int getOffsetOrDefault() {
    return Optional.ofNullable(offset).orElse(0);
  }

  @JsonIgnore
  public int getLimitOrDefault() {
    return Optional.ofNullable(limit).orElse(FullFilterBean.DEFAULT_LIMIT);
  }
}
