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
package org.factcast.factus.lock;

import static org.factcast.factus.metrics.TagKeys.CLASS;

import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.Fact;
import org.factcast.core.FactCast;
import org.factcast.core.lock.Attempt;
import org.factcast.core.lock.AttemptAbortedException;
import org.factcast.core.lock.PublishingResult;
import org.factcast.core.spec.FactSpec;
import org.factcast.factus.Factus;
import org.factcast.factus.event.EventObject;
import org.factcast.factus.metrics.CountedEvent;
import org.factcast.factus.metrics.FactusMetrics;
import org.factcast.factus.projection.*;

@RequiredArgsConstructor
@Slf4j
@Data
@SuppressWarnings("unused")
public class Locked<I extends Projection> {
  private static final String MANUAL_FACT_SPECS = "manualFactSpecs";

  @NonNull private final FactCast fc;

  @NonNull private final Factus factus;

  private final I projectionOrNull;

  @NonNull private final List<FactSpec> specs;

  @NonNull private final FactusMetrics factusMetrics;

  int retries = 10;

  long intervalMillis = 0;

  public void attempt(BiConsumer<I, RetryableTransaction> tx) {
    attempt(tx, result -> null);
  }

  public void attempt(BiConsumer<I, RetryableTransaction> tx, Runnable e) {
    attempt(
        tx,
        f -> {
          e.run();
          return null;
        });
  }

  @SuppressWarnings("UnusedReturnValue")
  public <R> R attempt(
      BiConsumer<I, RetryableTransaction> bodyToExecute, Function<List<Fact>, R> resultFn) {
    try {
      PublishingResult result =
          fc.lock(specs)
              .optimistic()
              .retry(retries())
              .interval(intervalMillis())
              .attempt(
                  () -> {
                    try {
                      I updatedProjection = null;

                      if (projectionOrNull != null) {
                        factusMetrics.count(
                            CountedEvent.TRANSACTION_ATTEMPTS,
                            Tags.of(Tag.of(CLASS, projectionOrNull.getClass().getName())));
                        updatedProjection = update(projectionOrNull);
                      } else {

                        factusMetrics.count(
                            CountedEvent.TRANSACTION_ATTEMPTS,
                            Tags.of(Tag.of(CLASS, MANUAL_FACT_SPECS)));
                      }

                      List<Supplier<Fact>> toPublish =
                          Collections.synchronizedList(new LinkedList<>());
                      RetryableTransaction txWithLockOnSpecs = createTransaction(factus, toPublish);

                      try {
                        InLockedOperation.enterLockedOperation();
                        bodyToExecute.accept(updatedProjection, txWithLockOnSpecs);
                        return Attempt.publishUnlessEmpty(
                            toPublish.stream().map(Supplier::get).collect(Collectors.toList()));
                      } finally {
                        InLockedOperation.exitLockedOperation();
                      }
                    } catch (LockedOperationAbortedException aborted) {
                      throw aborted;
                    } catch (Throwable e) {
                      throw LockedOperationAbortedException.wrap(e);
                    }
                  });

      return resultFn.apply(result.publishedFacts());

    } catch (AttemptAbortedException e) {
      if (projectionOrNull != null) {
        factusMetrics.count(
            CountedEvent.TRANSACTION_ABORT,
            Tags.of(Tag.of(CLASS, projectionOrNull.getClass().getName())));
      } else {
        factusMetrics.count(
            CountedEvent.TRANSACTION_ABORT, Tags.of(Tag.of(CLASS, MANUAL_FACT_SPECS)));
      }

      throw LockedOperationAbortedException.wrap(e);
    }
  }

  @SuppressWarnings("unchecked")
  private I update(I projection) {

    if (projection == null) {
      return null;
    }

    if (projection instanceof Aggregate) {
      Class<? extends Aggregate> projectionClass =
          (Class<? extends Aggregate>) projection.getClass();
      return (I) factus.fetch(projectionClass, AggregateUtil.aggregateId((Aggregate) projection));
    }
    if (projection instanceof SnapshotProjection) {
      Class<? extends SnapshotProjection> projectionClass =
          (Class<? extends SnapshotProjection>) projection.getClass();
      return (I) factus.fetch(projectionClass);
    }
    if (projection instanceof ManagedProjection) {
      factus.update((ManagedProjection) projection);
      return projection;
    }
    throw new IllegalStateException("Don't know how to update " + projection);
  }

  private RetryableTransaction createTransaction(Factus factus, List<Supplier<Fact>> toPublish) {
    return new RetryableTransaction() {
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
      public <T extends ProjectorContext, P extends ManagedProjection<T>> void update(
          @NonNull P managedProjection, @NonNull Duration maxWaitTime) throws TimeoutException {
        factus.update(managedProjection, maxWaitTime);
      }
    };
  }
}
