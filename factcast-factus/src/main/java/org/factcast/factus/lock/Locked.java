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
import java.util.*;
import java.util.function.*;
import java.util.stream.*;
import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.Fact;
import org.factcast.core.FactCast;
import org.factcast.core.lock.*;
import org.factcast.core.spec.FactSpec;
import org.factcast.factus.Factus;
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

  long intervalMillis;

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
                      RetryableTransactionImpl txWithLockOnSpecs =
                          createTransaction(factus, toPublish);

                      try {
                        InLockedOperation.enterLockedOperation();
                        bodyToExecute.accept(updatedProjection, txWithLockOnSpecs);
                        IntermediatePublishResult im =
                            Attempt.publishUnlessEmpty(
                                toPublish.stream().map(Supplier::get).collect(Collectors.toList()));
                        txWithLockOnSpecs.onSuccess().ifPresent(im::andThen);
                        return im;
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

    if (projection instanceof Aggregate aggregate) {
      Class<? extends Aggregate> projectionClass =
          (Class<? extends Aggregate>) projection.getClass();
      return (I) factus.fetch(projectionClass, AggregateUtil.aggregateId(aggregate));
    }
    if (projection instanceof SnapshotProjection) {
      Class<? extends SnapshotProjection> projectionClass =
          (Class<? extends SnapshotProjection>) projection.getClass();
      return (I) factus.fetch(projectionClass);
    }
    if (projection instanceof ManagedProjection managedProjection) {
      factus.update(managedProjection);
      return projection;
    }
    throw new IllegalStateException("Don't know how to update " + projection);
  }

  private RetryableTransactionImpl createTransaction(
      Factus factus, List<Supplier<Fact>> toPublish) {
    return new RetryableTransactionImpl(toPublish, factus);
  }
}
