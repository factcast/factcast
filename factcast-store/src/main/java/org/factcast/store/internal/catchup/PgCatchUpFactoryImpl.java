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
package org.factcast.store.internal.catchup;

import java.util.concurrent.atomic.*;
import lombok.NonNull;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.store.StoreConfigurationProperties;
import org.factcast.store.internal.PgMetrics;
import org.factcast.store.internal.catchup.chunked.PgChunkedCatchup;
import org.factcast.store.internal.catchup.chunkedwithhold.PgChunkedWithHoldCursorCatchup;
import org.factcast.store.internal.catchup.cursor.PgCursorCatchup;
import org.factcast.store.internal.pipeline.ServerPipeline;
import org.factcast.store.internal.query.CurrentStatementHolder;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

public class PgCatchUpFactoryImpl implements PgCatchupFactory {

  @NonNull final StoreConfigurationProperties props;
  @NonNull final PgMetrics metrics;

  public PgCatchUpFactoryImpl(
      @NonNull StoreConfigurationProperties props, @NonNull PgMetrics metrics) {
    this.props = props;
    this.metrics = metrics;
  }

  @Override
  public PgCatchup create(
      @NonNull SubscriptionRequestTO request,
      @NonNull ServerPipeline pipeline,
      @NonNull AtomicLong serial,
      @NonNull CurrentStatementHolder holder,
      @NonNull SingleConnectionDataSource ds,
      @NonNull Phase phase) {

    // does not make sense to use in phase 2 altogether, as we're not expecting many facts there.
    if (phase == Phase.PHASE_2) {
      return new PgCursorCatchup(props, metrics, request, pipeline, serial, holder, ds, phase);
    }

    return switch (props.getCatchupStrategy()) {
      case CHUNKED ->
          new PgChunkedCatchup(props, metrics, request, pipeline, serial, holder, ds, phase);
      case CHUNKED_WITH_HOLD ->
          new PgChunkedWithHoldCursorCatchup(
              props, metrics, request, pipeline, serial, holder, ds, phase);
      case CURSOR ->
          new PgCursorCatchup(props, metrics, request, pipeline, serial, holder, ds, phase);
    };
  }
}
