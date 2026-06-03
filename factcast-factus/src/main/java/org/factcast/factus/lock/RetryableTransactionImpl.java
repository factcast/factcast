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
package org.factcast.factus.lock;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import lombok.NonNull;
import org.factcast.core.Fact;
import org.factcast.factus.Factus;
import org.factcast.factus.event.EventObject;
import org.factcast.factus.projection.*;

class RetryableTransactionImpl implements RetryableTransaction {

  private final List<Supplier<Fact>> toPublish;
  private final Factus factus;
  private Runnable onSuccess;

  public RetryableTransactionImpl(@NonNull List<Supplier<Fact>> toPublish, @NonNull Factus factus) {
    this.toPublish = toPublish;
    this.factus = factus;
    onSuccess = null;
  }

  @Override
  public void onSuccess(@NonNull Runnable willBeRunOnSuccessOnly) {
    if (this.onSuccess == null) {
      onSuccess = willBeRunOnSuccessOnly;
    } else {
      Runnable formerRunnable = onSuccess;
      onSuccess =
          () -> {
            formerRunnable.run();
            willBeRunOnSuccessOnly.run();
          };
    }
  }

  public Optional<Runnable> onSuccess() {
    return Optional.ofNullable(onSuccess);
  }

  @Override
  public void publish(@NonNull EventObject e) {
    toPublish.add(() -> factus.toFact(e));
  }

  @Override
  public void publish(@NonNull List<EventObject> eventPojos) {
    eventPojos.forEach(this::publish);
  }

  @Override
  public void publish(@NonNull Fact e) {
    toPublish.add(() -> e);
  }

  @Override
  public <P extends SnapshotProjection> P fetch(@NonNull Class<P> projectionClass) {
    return factus.fetch(projectionClass);
  }

  @Override
  public <A extends Aggregate> Optional<A> find(
      @NonNull Class<A> aggregateClass, @NonNull UUID aggregateId) {
    return factus.find(aggregateClass, aggregateId);
  }

  @Override
  public <P extends ManagedProjection> void update(
      @NonNull P managedProjection, @NonNull Duration maxWaitTime) throws TimeoutException {
    factus.update(managedProjection, maxWaitTime);
  }
}
