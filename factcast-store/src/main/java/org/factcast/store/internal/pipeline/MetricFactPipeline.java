/*
 * Copyright © 2017-2024 factcast.org
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
package org.factcast.store.internal.pipeline;

import lombok.NonNull;
import org.factcast.core.Fact;
import org.factcast.store.internal.PgMetrics;
import org.factcast.store.internal.StoreMetrics;
import org.jetbrains.annotations.Nullable;

public class MetricFactPipeline extends AbstractFactPipeline {
  @NonNull final PgMetrics metrics;

  public MetricFactPipeline(@NonNull FactPipeline parent, @NonNull PgMetrics metrics) {
    super(parent);
    this.metrics = metrics;
  }

  @Override
  public void fact(@Nullable Fact fact) {
    if (fact != null) metrics.counter(StoreMetrics.EVENT.CATCHUP_FACT).increment();
    parent.fact(fact);
  }

  @Override
  public void error(@NonNull Throwable err) {}
}
