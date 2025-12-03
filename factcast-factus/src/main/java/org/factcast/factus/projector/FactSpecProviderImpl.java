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

import java.util.*;
import lombok.*;
import org.factcast.core.spec.FactSpec;
import org.factcast.factus.projection.*;
import org.factcast.factus.projection.parameter.HandlerParameterContributors;

@RequiredArgsConstructor
public class FactSpecProviderImpl implements FactSpecProvider {

  final ProjectorFactory pf;

  private final HandlerParameterContributors contrib;

  // we should not try to reuse (cache) Projection instances or projectors here,
  // as they might hold state that is relevant for their FactSpec generation (id, for aggregates
  // for instance)
  //
  // While projector initialization is somewhat costly, FactSpec generation is not on the hot path,

  @Override
  public @NonNull Collection<FactSpec> forSnapshot(
      @NonNull Class<? extends SnapshotProjection> clazz) {
    Projection p = ReflectionUtils.newInstance(clazz);
    return pf.create(p).createFactSpecs();
  }

  @Override
  public @NonNull Collection<FactSpec> forAggregate(
      @NonNull Class<? extends Aggregate> clazz, @NonNull UUID aggregateId) {
    Aggregate p = ReflectionUtils.newInstance(clazz);
    AggregateUtil.aggregateId(p, aggregateId);
    return pf.create(p).createFactSpecs();
  }
}
