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
package org.factcast.store.internal;

import com.google.common.collect.Lists;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.subscription.observer.HighWaterMarkFetcher;
import org.factcast.store.internal.listen.*;
import org.factcast.store.internal.pipeline.*;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.datasource.*;

/**
 * executes a query in a synchronized fashion, to make sure, results are processed in order as well
 * as sequentially.
 *
 * <p>Note, that you can hint the query method if index usage is wanted. In a catchup scenario, you
 * will probably want to use an index. If however you are following a fact stream and expect to get
 * a low number of rows (if any) back from the query because you seek for the "latest" changes, it
 * is way more efficient to scan the table. In that case call <code>query(false)</code>.
 *
 * <p>DO NOT use an instance as a singleton/Spring bean. This class is meant be instantiated by each
 * subscription.
 *
 * @author uwe.schaefer@prisma-capacity.eu
 */
@Slf4j
class PgSynchronizedQuery {

  @NonNull final String sql;

  @NonNull final PreparedStatementSetter setter;

  @NonNull final RowCallbackHandler rowHandler;

  @NonNull final String debugInfo;
  @NonNull final PushbackServerPipeline pipe;
  @NonNull final AtomicLong serialToContinueFrom;

  @NonNull final HighWaterMarkFetcher hwmFetcher;

  private final @NonNull PgConnectionSupplier connectionSupplier;

  PgSynchronizedQuery(
      @NonNull String debugInfo,
      @NonNull PushbackServerPipeline pipe,
      @NonNull PgConnectionSupplier connectionSupplier,
      @NonNull String sql,
      @NonNull PreparedStatementSetter setter,
      @NonNull Supplier<Boolean> isConnected,
      @NonNull AtomicLong serialToContinueFrom,
      @NonNull HighWaterMarkFetcher hwmFetcher) {
    this.debugInfo = debugInfo;
    this.pipe = pipe;
    this.serialToContinueFrom = serialToContinueFrom;
    this.hwmFetcher = hwmFetcher;
    this.connectionSupplier = connectionSupplier;
    this.sql = sql;
    this.setter = setter;

    rowHandler =
        new PgSynchronizedQuery.FactRowCallbackHandler(pipe, isConnected, serialToContinueFrom);
  }

  // the synchronized here is crucial!
  @SuppressWarnings({"SameReturnValue", "java:S1181"})
  public synchronized void run(boolean useIndex) throws PipelineAlreadyClosedException {
    List<ConnectionModifier> filters =
        Lists.newArrayList(ConnectionModifier.withApplicationName(debugInfo));
    if (!useIndex) {
      filters.add(ConnectionModifier.withBitmapScanDisabled());
    } else {
      // if we want to use gin indexes, we need to force custom plans to hit the partial index for
      // the latest facts
      filters.add(ConnectionModifier.withCustomPlanForced());
    }

    // it does not make much sense to track the statement here, as we expect this to be executed
    // quickly, as we're in  afloow scenarion
    try (SingleConnectionDataSource ds = connectionSupplier.getPooledAsSingleDataSource(filters)) {
      long latest = hwmFetcher.highWaterMark(ds).targetSer();
      new JdbcTemplate(ds).query(sql, setter, rowHandler);

      // shift to max(retrievedLatestSer, and ser as updated in
      // rowHandler)
      serialToContinueFrom.set(Math.max(latest, serialToContinueFrom.get()));
    } finally {
      try {
        // involves transformation & IO, so can throw exception
        pipe.process(Signal.flush());
      } catch (Throwable e) {
        // this is necessary to end this subscription, so that the client can resubscribe using the
        // FSP it received.
        // Note that the FSP assigned to this subscription might already be ahead, so that we would
        // run in the danger of skipping events.
        // see #4127
        pipe.process(Signal.of(e));
      }
    }
  }

  @RequiredArgsConstructor
  static class FactRowCallbackHandler implements RowCallbackHandler {
    final PushbackServerPipeline pipe;

    final Supplier<Boolean> isConnectedSupplier;

    final AtomicLong serial;

    @SuppressWarnings("NullableProblems")
    @Override
    public void processRow(ResultSet rs) throws SQLException {
      if (Boolean.TRUE.equals(isConnectedSupplier.get())) {
        if (rs.isClosed()) {
          throw new IllegalStateException(
              "ResultSet already closed. We should not have gotten here. THIS IS A BUG!");
        }
        PgFact f = null;
        try {
          f = PgFact.from(rs);
          pipe.process(Signal.of(f));
          serial.set(rs.getLong(PgConstants.COLUMN_SER));
        } catch (Exception e) {
          escalateError(rs, e);
        }
      }
    }

    private void escalateError(ResultSet rs, Throwable e) {
      try {
        rs.close();
      } catch (Exception ignore) {
        // this one will be ignored
      }

      try {
        pipe.process(Signal.of(e));
      } catch (PipelineAlreadyClosedException meh) {
        // can be ignored
      }
    }
  }
}
