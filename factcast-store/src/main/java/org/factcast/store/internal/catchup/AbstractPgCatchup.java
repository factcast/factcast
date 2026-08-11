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

import java.sql.*;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import javax.sql.DataSource;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.jdbc.pool.PooledConnection;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.core.util.ExceptionHelper;
import org.factcast.store.StoreConfigurationProperties;
import org.factcast.store.internal.PgMetrics;
import org.factcast.store.internal.pipeline.ServerPipeline;

@RequiredArgsConstructor
@SuppressWarnings("java:S107")
@Slf4j
public abstract class AbstractPgCatchup implements PgCatchup {

  public static final Duration FIRST_ROW_FETCHING_THRESHOLD = Duration.ofSeconds(1);

  protected long fastForward = 0;

  @NonNull protected final StoreConfigurationProperties props;
  @NonNull protected final PgMetrics metrics;
  @NonNull protected final SubscriptionRequestTO req;
  @NonNull protected final ServerPipeline pipeline;
  @NonNull protected final AtomicLong serial;
  @NonNull protected final DataSource ds;
  @NonNull protected final PgCatchupFactory.Phase phase;

  @Override
  public final void fastForward(long serialToStartFrom) {
    this.fastForward = serialToStartFrom;
  }

  protected boolean wasCancelled() {
    try {
      Connection connection = ds.getConnection();
      PooledConnection pooled = connection.unwrap(PooledConnection.class);
      boolean discarded = pooled.isDiscarded();
      if (discarded) log.debug("{} catchup was cancelled", req);
      return discarded;
    } catch (SQLException e) {
      throw ExceptionHelper.toRuntime(e);
    }
  }

  public final void assertWasNotCancelled() {
    if (wasCancelled())
      throw new IllegalStateException(
          "Connection was discarded. This happens after cancelling and might be rare, but normal");
  }

  @SuppressWarnings("java:S2095")
  protected final void markConnectionDone() throws SQLException {
    Connection connection = ds.getConnection();
    if (!connection.getAutoCommit()) {
      connection.rollback(); // marks the process as regularly complete
      connection.setAutoCommit(true);
    }
  }
}
