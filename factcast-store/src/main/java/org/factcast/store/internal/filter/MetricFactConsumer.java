/*
 * Copyright © 2017-2022 factcast.org
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
package org.factcast.store.internal.filter;

import io.micrometer.core.instrument.Counter;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.Fact;
import org.factcast.store.internal.PgMetrics;
import org.factcast.store.internal.StoreMetrics;

@Slf4j
public class MetricFactConsumer implements Consumer<Fact> {

  @NonNull private final Consumer<Fact> parent;
  private final Counter count;

  public MetricFactConsumer(@NonNull Consumer<Fact> parent, @NonNull PgMetrics metrics) {
    this.parent = parent;
    this.count = metrics.counter(StoreMetrics.EVENT.CATCHUP_FACT);
  }

  @Override
  public void accept(@Nullable Fact fact) {
    parent.accept(fact);
    if (fact != null) count.increment();
  }
}
