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

import java.util.concurrent.ExecutorService;
import lombok.Builder;
import lombok.NonNull;
import org.factcast.core.subscription.SubscriptionImpl;
import org.factcast.core.subscription.SubscriptionRequest;
import org.factcast.core.subscription.transformation.FactTransformerService;
import org.factcast.core.subscription.transformation.FactTransformers;
import org.factcast.store.internal.PgMetrics;
import org.factcast.store.internal.PostQueryMatcher;
import org.factcast.store.internal.filter.blacklist.Blacklist;
import org.factcast.store.internal.script.JSEngineFactory;

@Builder
public class FactPipelineFactory {

  @NonNull final PgMetrics metrics;
  @NonNull final Blacklist blacklist;
  @NonNull final FactTransformerService factTransformerService;
  @NonNull final JSEngineFactory jsEngineFactory;
  private ExecutorService executorService;

  public FactPipeline create(
      @NonNull SubscriptionRequest subreq,
      @NonNull SubscriptionImpl sub,
      @NonNull PostQueryMatcher perRequestMatcher,
      int maxBufferSize,
      @NonNull ExecutorService executorService) {
    this.executorService = executorService;

    FactPipeline chain = new BaseFactPipeline(sub);
    chain = new MetricFactPipeline(chain, metrics);

    chain =
        new TransformingFactPipeline(
            chain, factTransformerService, FactTransformers.createFor(subreq));
    chain = new BlacklistFilterFactPipeline(chain, blacklist);
    chain = new PostQueryFilterFactPipeline(chain, perRequestMatcher);

    return chain;
  }
}
