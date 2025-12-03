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
package org.factcast.factus.aggregates;

import java.util.Optional;
import lombok.*;
import org.factcast.factus.Factus;
import org.factcast.factus.projection.Aggregate;

@RequiredArgsConstructor
public class AggregateRepositoryImpl<I extends AggregateIdentifier, A extends Aggregate>
    implements AggregateRepository<I, A> {

  private final Class<A> aggregateClass;
  private final Factus factus;

  @Override
  public @NonNull Optional<A> find(@NonNull I id) {
    return factus.find(aggregateClass, id.getId());
  }
}
