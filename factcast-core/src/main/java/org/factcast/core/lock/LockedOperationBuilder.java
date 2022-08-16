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
package org.factcast.core.lock;

import java.util.*;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.factcast.core.lock.WithOptimisticLock.OptimisticRetriesExceededException;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.store.FactStore;

@RequiredArgsConstructor
public final class LockedOperationBuilder {
  @NonNull final FactStore store;

  final List<FactSpec> specs;

  public WithOptimisticLock optimistic() {
    return new WithOptimisticLock(store, specs);
  }

  // we MIGHT add pessimistic if we REALLY REALLY have to

  /**
   * convenience method that uses optimistic locking with defaults. Alternatively, you can call
   * optimistic() to get control over the optimistic settings.
   *
   * @param operation will be attempted to be executed, maybe many times
   * @return id of the last fact published
   * @throws OptimisticRetriesExceededException if max number of retries are reached
   * @throws ExceptionAfterPublish if andThen-block throws an exception
   * @throws AttemptAbortedException if calling Attempt.abort, operation will not be retried
   */
  public @NonNull PublishingResult attempt(@NonNull Attempt operation)
      throws OptimisticRetriesExceededException, ExceptionAfterPublish, AttemptAbortedException {
    return optimistic().attempt(operation);
  }
}
