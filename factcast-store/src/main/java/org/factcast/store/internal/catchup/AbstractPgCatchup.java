/*
 * Copyright © 2017-2026 factcast.org
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

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import javax.sql.DataSource;
import lombok.*;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.store.StoreConfigurationProperties;
import org.factcast.store.internal.PgMetrics;
import org.factcast.store.internal.pipeline.ServerPipeline;
import org.factcast.store.internal.query.CurrentStatementHolder;

@RequiredArgsConstructor
@SuppressWarnings("java:S107")
public abstract class AbstractPgCatchup implements PgCatchup {

  public static final Duration FIRST_ROW_FETCHING_THRESHOLD = Duration.ofSeconds(1);

  protected long fastForward = 0;

  @NonNull protected final StoreConfigurationProperties props;
  @NonNull protected final PgMetrics metrics;
  @NonNull protected final SubscriptionRequestTO req;
  @NonNull protected final ServerPipeline pipeline;
  @NonNull protected final AtomicLong serial;
  @NonNull protected final CurrentStatementHolder statementHolder;
  @NonNull protected final DataSource ds;
  @NonNull protected final PgCatchupFactory.Phase phase;

  @Override
  public final void fastForward(long serialToStartFrom) {
    this.fastForward = serialToStartFrom;
  }
}
