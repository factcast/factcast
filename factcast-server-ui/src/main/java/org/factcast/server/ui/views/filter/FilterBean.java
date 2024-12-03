/*
 * Copyright © 2017-2024 factcast.org
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.NonNull;
import org.factcast.core.spec.FactSpec;

public interface FilterBean {

  LocalDate getSince();

  void setSince(LocalDate since);

  Integer getLimit();

  void setLimit(Integer limit);

  Integer getOffset();

  void setOffset(Integer offset);

  BigDecimal getFrom();

  void setFrom(BigDecimal from);

  List<FactCriteria> getCriteria();

  @NonNull
  default List<FactSpec> createFactSpecs() {
    return getCriteria().stream().flatMap(FactCriteria::createFactSpecs).toList();
  }

  void reset();

  int getOffsetOrDefault();

  int getLimitOrDefault();
}