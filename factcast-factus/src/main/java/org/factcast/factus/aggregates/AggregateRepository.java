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

import java.lang.reflect.ParameterizedType;
import java.util.Optional;
import lombok.NonNull;
import org.factcast.factus.Factus;
import org.factcast.factus.projection.Aggregate;

public interface AggregateRepository<I extends AggregateIdentifier, A extends Aggregate> {

  /**
   * Finds an aggregate by its identifier.
   *
   * @param id the identifier of the aggregate
   * @return an optional containing the aggregate if found, or empty if not found
   */
  @NonNull
  Optional<A> find(@NonNull I id);

  /**
   * Fetches an aggregate by its identifier, throwing an exception if not found.
   *
   * @param id the identifier of the aggregate
   * @return the aggregate if found
   * @throws AggregateNotFoundException if the aggregate is not found
   */
  @NonNull
  default A fetch(@NonNull I id) {
    return find(id).orElseThrow(() -> new AggregateNotFoundException(id));
  }

  default Class<A> aggregateType() {
    //noinspection unchecked
    return (Class<A>)
        ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[1];
  }

  /** just as a hint */
  static <I extends AggregateIdentifier, A extends Aggregate> AggregateRepository<I, A> create(
      @NonNull Class<A> aggregateClass, @NonNull Factus factus) {
    return new AggregateRepositoryImpl<>(aggregateClass, factus);
  }
}
