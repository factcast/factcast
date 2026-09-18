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
package org.factcast.factus.projector;

import java.util.UUID;
import lombok.Getter;
import org.factcast.core.FactHeader;
import org.factcast.factus.FilterByAggIdProperty;
import org.factcast.factus.Handler;
import org.factcast.factus.projection.Aggregate;

/**
 * the filtered EventObject is not the first parameter here, see also the event's parameter index
 */
class FilterByAggIdPropertyHeaderFirstAggregate extends Aggregate {

  FilterByAggIdPropertyHeaderFirstAggregate(UUID aggregateId) {
    super(aggregateId);
  }

  @Getter private int appliedCount = 0;

  @Handler
  @FilterByAggIdProperty("recommendedUserId")
  void apply(FactHeader header, FilterByAggIdPropertyEvent e) {
    appliedCount++;
  }
}
