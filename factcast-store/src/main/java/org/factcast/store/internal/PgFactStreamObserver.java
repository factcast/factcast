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
package org.factcast.store.internal;

import java.util.List;
import java.util.function.Consumer;
import lombok.NonNull;
import org.factcast.core.Fact;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.core.subscription.observer.FactStreamObserver;
import org.factcast.core.subscription.transformation.FactTransformerService;
import org.factcast.core.subscription.transformation.FactTransformers;
import org.factcast.store.internal.filter.*;
import org.factcast.store.internal.filter.blacklist.Blacklist;
import org.factcast.store.internal.script.JSEngineFactory;
import org.jetbrains.annotations.Nullable;

// TODO needs to be refactored out into a store-common kind of module
public class PgFactStreamObserver extends FactStreamObserver {
  private final Consumer<Fact> consumer;

  public PgFactStreamObserver(
      @NonNull FactStreamObserver observer,
      @NonNull SubscriptionRequestTO req,
      @NonNull Blacklist bl,
      @NonNull FactTransformers transformers,
      @NonNull FactTransformerService transformerService,
      @NonNull JSEngineFactory jsef,
      @NonNull PgMetrics metrics) {
    super(observer);

    consumer =
        new PostQueryFilteringFactConsumer(
            new BlacklistFilteringFactConsumer(
                new TransformingFactConsumer(
                    new MetricFactConsumer(super::onNext, metrics),
                    transformerService,
                    transformers,
                    metrics),
                req,
                bl),
            new PostQueryMatcher(req, jsef));
  }

  @Override
  public void onNext(@Nullable Fact f) {
    consumer.accept(f);
  }

  @Override
  public void onNext(@NonNull List<Fact> facts) {
    throw new FactStreamObserverGranularityException(
        "we know that on the server side we do not want any batching other than what is done on the GRPC layer");
    // instead of facts.forEach(this::onNext);
  }
}
