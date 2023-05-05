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
package org.factcast.store.internal.catchup.tmppaged;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.*;
import lombok.Generated;
import lombok.NonNull;
import org.factcast.core.subscription.SubscriptionImpl;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.core.subscription.transformation.FactTransformerService;
import org.factcast.core.subscription.transformation.FactTransformers;
import org.factcast.store.StoreConfigurationProperties;
import org.factcast.store.internal.PgMetrics;
import org.factcast.store.internal.catchup.BufferingFactInterceptor;
import org.factcast.store.internal.catchup.PgCatchupFactory;
import org.factcast.store.internal.filter.FactFilter;
import org.factcast.store.internal.listen.PgConnectionSupplier;
import org.factcast.store.internal.query.CurrentStatementHolder;

// no code in here, just generated @nonnull checks
@Generated
public class PgTmpPagedCatchUpFactory implements PgCatchupFactory, AutoCloseable {

  @NonNull final PgConnectionSupplier connectionSupplier;
  @NonNull final StoreConfigurationProperties props;
  @NonNull final PgMetrics metrics;
  @NonNull final FactTransformerService transformerService;

  private final ExecutorService executorService;

  public PgTmpPagedCatchUpFactory(
      @NonNull PgConnectionSupplier connectionSupplier,
      @NonNull StoreConfigurationProperties props,
      @NonNull PgMetrics metrics,
      @NonNull FactTransformerService transformerService) {
    this.connectionSupplier = connectionSupplier;
    this.props = props;
    this.metrics = metrics;
    this.transformerService = transformerService;
    this.executorService =
        metrics.monitor(
            Executors.newWorkStealingPool(props.getSizeOfThreadPoolForBufferedTransformations()),
            "fetching-catchup");
  }

  @Override
  public PgTmpPagedCatchup create(
      @NonNull SubscriptionRequestTO request,
      @NonNull SubscriptionImpl subscription,
      @NonNull FactFilter factFilter,
      @NonNull AtomicLong serial,
      @NonNull CurrentStatementHolder statementHolder) {
    return new PgTmpPagedCatchup(
        connectionSupplier,
        props,
        request,
        new BufferingFactInterceptor(
            transformerService,
            FactTransformers.createFor(request),
            factFilter,
            subscription,
            props.getPageSize(),
            metrics,
            executorService),
        serial,
        statementHolder);
  }

  @Override
  public void close() throws Exception {
    executorService.shutdown();
  }
}
