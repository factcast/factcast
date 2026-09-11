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
package org.factcast.itests.factus.proj;

import lombok.Getter;
import org.factcast.factus.FilterByAggIdProperty;
import org.factcast.factus.Handler;
import org.factcast.factus.projection.Aggregate;
import org.factcast.factus.serializer.ProjectionMetaData;
import org.factcast.itests.factus.event.UserFired;

/**
 * Aggregate that should only be considered "fired" when it is the <em>sacked</em> user of a {@link
 * UserFired} event, never when it is merely the actor (sacker). {@code UserFired} carries both ids
 * as aggregate ids, so without {@link FilterByAggIdProperty} the actor's aggregate would wrongly
 * consume the event.
 */
@ProjectionMetaData(revision = 1)
public class FiredUserAggregate extends Aggregate {

  @Getter private boolean fired = false;

  @Handler
  @FilterByAggIdProperty("sackedId")
  void apply(UserFired e) {
    fired = true;
  }
}
