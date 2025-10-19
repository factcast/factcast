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

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.factcast.core.Fact;
import org.factcast.core.FactCast;
import org.factcast.core.spec.FactSpec;
import org.factcast.factus.Factus;
import org.factcast.factus.metrics.FactusMetrics;
import org.factcast.factus.projection.Projection;

@RequiredArgsConstructor
@Getter
public class LockedOnSpecs {

  @NonNull final FactCast fc;
  @NonNull final Factus factus;
  @NonNull private final InLockedOperation inLockedOperation;
  @NonNull final List<FactSpec> specs;
  @NonNull final FactusMetrics metrics;

  @Setter
  @Accessors(fluent = true, chain = true)
  Integer retries;

  @Setter
  @Accessors(fluent = true, chain = true)
  Long intervalMillis;

  public void attempt(Consumer<RetryableTransaction> tx) {
    attempt(tx, result -> null);
  }

  public void attempt(Consumer<RetryableTransaction> tx, Runnable e) {
    attempt(
        tx,
        f -> {
          e.run();
          return null;
        });
  }

  @SuppressWarnings({"UnusedReturnValue", "rawtypes"})
  public <R, I extends Projection> R attempt(
      Consumer<RetryableTransaction> consumer, Function<List<Fact>, R> resultFn) {
    Locked<I> delegate = new Locked<I>(fc, factus, inLockedOperation, null, specs, metrics);

    if (retries != null) {
      delegate.retries(retries);
    }

    if (intervalMillis != null) {
      delegate.intervalMillis(intervalMillis);
    }

    BiConsumer<I, RetryableTransaction> biConsumer = (x, tx) -> consumer.accept(tx);
    return delegate.attempt(biConsumer, resultFn);
  }
}
