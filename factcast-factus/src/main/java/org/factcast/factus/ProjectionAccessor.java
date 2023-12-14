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
package org.factcast.factus;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import lombok.NonNull;
import lombok.SneakyThrows;
import org.factcast.factus.projection.Aggregate;
import org.factcast.factus.projection.ManagedProjection;
import org.factcast.factus.projection.ProjectorContext;
import org.factcast.factus.projection.SnapshotProjection;

public interface ProjectionAccessor {
  ///// PROJECTIONS

  // snapshot projections:

  /**
   * If there is a matching snapshot already, it is deserialized and the matching events, which are
   * not yet applied, will be. Afterwards, a new snapshot is created and stored.
   *
   * <p>If there is no existing snapshot yet, or they are not matching (see serialVersionUID), an
   * initial one will be created.
   *
   * @return an instance of the projectionClass in at least initial state, and (if there are any)
   *     with all currently published facts applied.
   */
  @NonNull
  <P extends SnapshotProjection> P fetch(@NonNull Class<P> projectionClass);

  /**
   * Same as fetching on a snapshot projection, but limited to one aggregateId. If no fact was
   * found, Optional.empty will be returned
   */
  @NonNull
  <A extends Aggregate> Optional<A> find(
      @NonNull Class<A> aggregateClass, @NonNull UUID aggregateId);

  /**
   * shortcut to find, but returns the aggregate unwrapped. throws {@link IllegalStateException} if
   * the aggregate does not exist yet.
   */
  @NonNull
  default <A extends Aggregate> A fetch(
      @NonNull Class<A> aggregateClass, @NonNull UUID aggregateId) {
    return find(aggregateClass, aggregateId)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Aggregate of type "
                        + aggregateClass.getSimpleName()
                        + " for id "
                        + aggregateId
                        + " does not exist."));
  }

  // managed projections:

  /**
   * blockingly updates the given projection with default timeout
   *
   * <p>This method is thread-safe, even on the same projection.
   */
  @SneakyThrows
  default <T extends ProjectorContext, P extends ManagedProjection<T>> void update(
      @NonNull P managedProjection) {
    update(managedProjection, FactusConstants.FOREVER);
  }

  /**
   * blockingly updates the given projection with the given timeout. If that timeout is reached, a
   * timeoutexception will be thrown.
   *
   * <p>This method is thread-safe, even on the same projection.
   */
  <T extends ProjectorContext, P extends ManagedProjection<T>> void update(
      @NonNull P managedProjection, @NonNull Duration maxWaitTime) throws TimeoutException;
}
