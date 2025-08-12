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

import lombok.Builder;
import lombok.NonNull;
import org.factcast.core.subscription.SubscriptionImpl;
import org.factcast.core.subscription.SubscriptionRequest;
import org.factcast.core.subscription.transformation.FactTransformerService;
import org.factcast.core.subscription.transformation.FactTransformers;
import org.factcast.core.util.NoCoverageReportToBeGenerated;
import org.factcast.store.internal.PgMetrics;
import org.factcast.store.internal.PostQueryMatcher;
import org.factcast.store.internal.filter.blacklist.Blacklist;
import org.factcast.store.internal.script.JSEngineFactory;

@NoCoverageReportToBeGenerated("basically configuration code")
@Builder
public class ServerPipelineFactory {

  @NonNull final PgMetrics metrics;
  @NonNull final Blacklist blacklist;
  @NonNull final FactTransformerService factTransformerService;
  @NonNull final JSEngineFactory jsEngineFactory;

  public ServerPipeline create(
      @NonNull SubscriptionRequest subreq,
      @NonNull SubscriptionImpl sub,
      @NonNull PostQueryMatcher perRequestMatcher,
      int maxBufferSize) {

    ServerPipeline chain = new ServerPipelineAdapter(sub);
    chain = new MetricServerPipeline(chain, metrics, subreq);

    chain =
        new BufferedTransformingServerPipeline(
            chain, factTransformerService, FactTransformers.createFor(subreq), maxBufferSize);

    chain = new BlacklistFilterServerPipeline(chain, blacklist);
    chain = new PostQueryFilterServerPipeline(chain, perRequestMatcher);

    return chain;
  }
}
