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
import org.factcast.core.util.NoCoverageReportToBeGenerated;
import org.factcast.store.internal.*;
import org.factcast.store.internal.filter.blacklist.Blacklist;
import org.factcast.store.internal.script.JSEngineFactory;
import org.factcast.store.internal.transformation.FactTransformerService;
import org.factcast.store.internal.transformation.FactTransformers;

@NoCoverageReportToBeGenerated("basically configuration code")
@Builder
public class ServerPipelineFactory {

  @NonNull final PgMetrics metrics;
  @NonNull final Blacklist blacklist;
  @NonNull final FactTransformerService factTransformerService;
  @NonNull final JSEngineFactory jsEngineFactory;

  public ServerPipeline create(
      @NonNull SubscriptionRequest subreq, @NonNull SubscriptionImpl sub, int maxBufferSize) {

    ServerPipeline chain = new ServerPipelineAdapter(sub);
    chain = new MetricServerPipeline(chain, metrics);

    // needs to be executed AFTER transformation
    chain = new FilteringServerPipeline(chain, new FactFilter(subreq, jsEngineFactory));

    chain =
        new BufferedTransformingServerPipeline(
            chain, factTransformerService, FactTransformers.createFor(subreq), maxBufferSize);

    chain = new BlacklistFilterServerPipeline(chain, blacklist);

    return chain;
  }
}
