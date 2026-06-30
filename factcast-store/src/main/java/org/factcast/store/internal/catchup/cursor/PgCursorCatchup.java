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
package org.factcast.store.internal.catchup.cursor;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import java.sql.*;
import java.time.Duration;
import java.util.concurrent.atomic.*;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.store.StoreConfigurationProperties;
import org.factcast.store.internal.PgFact;
import org.factcast.store.internal.PgMetrics;
import org.factcast.store.internal.StoreMetrics;
import org.factcast.store.internal.catchup.AbstractPgCatchup;
import org.factcast.store.internal.catchup.PgCatchupFactory;
import org.factcast.store.internal.catchup.tools.fetching.FetchingQuery;
import org.factcast.store.internal.pipeline.ServerPipeline;
import org.factcast.store.internal.pipeline.Signal;
import org.factcast.store.internal.query.CurrentStatementHolder;
import org.factcast.store.internal.query.PgQueryBuilder;
import org.factcast.store.internal.rowmapper.PgFactExtractor;
import org.postgresql.util.PSQLException;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

@Slf4j
public class PgCursorCatchup extends AbstractPgCatchup {
  @SuppressWarnings("java:S107")
  public PgCursorCatchup(
      @NonNull StoreConfigurationProperties props,
      @NonNull PgMetrics metrics,
      @NonNull SubscriptionRequestTO req,
      @NonNull ServerPipeline pipeline,
      @NonNull AtomicLong serial,
      @NonNull CurrentStatementHolder statementHolder,
      @NonNull SingleConnectionDataSource ds,
      PgCatchupFactory.@NonNull Phase phase) {
    super(props, metrics, req, pipeline, serial, statementHolder, ds, phase);
  }

  @SneakyThrows
  @Override
  public void run() {
    try {
      // this needs to be transactional for fetch-size to have any effect whatsoever. luckily,
      // we use a org.springframework.jdbc.datasource.SingleConnectionDataSource with
      // autoCommitDisabled.

      Preconditions.checkState(
          !ds.getConnection().getAutoCommit(), "Connection must not be in autocommit mode");

      final var b = new PgQueryBuilder(req.specs(), statementHolder);
      final var extractor = new PgFactExtractor(serial);
      final var fromSerial = serial.get() < fastForward ? new AtomicLong(fastForward) : serial;
      final var catchupSQL = b.createSQL();
      final var isFromScratch = (fromSerial.get() <= 0);
      log.trace("{} catchup {} - facts starting with SER={}", req, phase, fromSerial.get());

      try (Connection conn = ds.getConnection();
          PreparedStatement prep = conn.prepareStatement(catchupSQL); ) {
        b.createStatementSetter(fromSerial).setValues(prep);
        prep.setFetchSize(props.getPageSize());
        prep.setQueryTimeout(0);

        final var timer = metrics.timer(StoreMetrics.OP.RESULT_STREAM_START, isFromScratch);
        final var timerSample = metrics.startSample();

        RowCallbackHandler rowCallbackHandler = createRowCallbackHandler(extractor);
        FetchingQuery.create(props)
            .executeAndProcess(
                prep,
                rowCallbackHandler::processRow,
                () -> logIfAboveThreshold(Duration.ofNanos(timerSample.stop(timer))));
      }
    } finally {
      statementHolder.clear();

      log.trace("Done fetching, flushing.");
      pipeline.process(Signal.flush());
    }
  }

  private void logIfAboveThreshold(Duration elapsed) {
    if (elapsed.compareTo(FIRST_ROW_FETCHING_THRESHOLD) > 0) {
      log.info("{} catchup - took {}s to stream the first result set", req, elapsed.toSeconds());
    }
  }

  @VisibleForTesting
  RowCallbackHandler createRowCallbackHandler(PgFactExtractor extractor) {
    return rs -> {
      try {
        if (statementHolder.wasCanceled() || rs.isClosed()) {
          return;
        }

        PgFact f = extractor.mapRow(rs, 0);
        pipeline.process(Signal.of(f));
      } catch (PSQLException psql) {
        // see #2088
        if (statementHolder.wasCanceled()) {
          // then we just swallow the exception
          log.trace("Swallowing because statement was cancelled", psql);
        } else {
          throw psql;
        }
      }
    };
  }
}
