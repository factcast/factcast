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

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import lombok.NonNull;
import org.factcast.core.Fact;
import org.factcast.core.FactCast;
import org.factcast.core.spec.FactSpec;
import org.factcast.factus.lock.Locked;
import org.factcast.factus.lock.RetryableTransaction;
import org.factcast.factus.metrics.FactusMetrics;
import org.factcast.factus.projection.Projection;

public class LockedOnSpecs<I extends Projection> {
  private final Locked<I> delegate;

  public LockedOnSpecs(
      @NonNull FactCast fc,
      @NonNull Factus factus,
      @NonNull List<FactSpec> specs,
      @NonNull FactusMetrics metrics) {
    this.delegate = new Locked<>(fc, factus, null, specs, metrics);
  }

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

  @SuppressWarnings("UnusedReturnValue")
  public <R> R attempt(Consumer<RetryableTransaction> consumer, Function<List<Fact>, R> resultFn) {
    BiConsumer<I, RetryableTransaction> biConsumer = (x, tx) -> consumer.accept(tx);
    return delegate.attempt(biConsumer, resultFn);
  }
}
