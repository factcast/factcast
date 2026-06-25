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
package org.factcast.store.internal.catchup.chunkedwithhold;

import com.google.common.annotations.VisibleForTesting;
import io.micrometer.core.instrument.Timer;
import java.sql.*;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.store.StoreConfigurationProperties;
import org.factcast.store.internal.*;
import org.factcast.store.internal.catchup.AbstractPgCatchup;
import org.factcast.store.internal.catchup.PgCatchupFactory;
import org.factcast.store.internal.pipeline.ServerPipeline;
import org.factcast.store.internal.pipeline.Signal;
import org.factcast.store.internal.query.CurrentStatementHolder;
import org.factcast.store.internal.query.PgQueryBuilder;
import org.factcast.store.internal.rowmapper.PgFactExtractor;
import org.postgresql.util.PSQLException;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
public class PgChunkedWithHoldCursorCatchup extends AbstractPgCatchup {

  private @NonNull DataSourceTransactionManager txMgr;

  @SuppressWarnings("java:S107")
  public PgChunkedWithHoldCursorCatchup(
      @NonNull StoreConfigurationProperties props,
      @NonNull PgMetrics metrics,
      @NonNull SubscriptionRequestTO req,
      @NonNull ServerPipeline pipeline,
      @NonNull AtomicLong serial,
      @NonNull CurrentStatementHolder statementHolder,
      @NonNull SingleConnectionDataSource ds,
      @NonNull PgCatchupFactory.Phase phase) {
    super(props, metrics, req, pipeline, serial, statementHolder, ds, phase);
  }

  @SneakyThrows
  @Override
  public void run() {
    final var cursorName = createCursorName();
    if (fetch(cursorName)) {
      log.trace("Done fetching, flushing.");
      pipeline.process(Signal.flush());
    }
  }

  /** returns true if fetch was not completely empty */
  @VisibleForTesting
  boolean fetch(String cursorName) throws SQLException {
    final var queryBuilder = new PgQueryBuilder(req.specs(), statementHolder);
    queryBuilder.serialsOnly();

    final var extractor = new PgFactExtractor(serial);
    final var fromSerial = new AtomicLong(Math.max(serial.get(), fastForward));
    final var fetchSQL = createFetchSql(cursorName);
    final var isFromScratch = (fromSerial.get() <= 0);
    final var timer = metrics.timer(StoreMetrics.OP.RESULT_STREAM_START, isFromScratch);
    final var timerSample = metrics.startSample();
    final var isFirstRow = new AtomicBoolean(false);
    if (statementHolder.wasCanceled()) return false;

    try {
      Boolean continueFetching =
          new TransactionTemplate(new JdbcTransactionManager(ds))
              .execute(
                  tx -> {
                    try {

                      Connection connection = ds.getConnection();

                      declareCursor(connection, cursorName, queryBuilder, fromSerial);

                      // as declaring the cursor could have taken some time, we'll check again
                      if (statementHolder.wasCanceled()) return false;

                      // the first fetch is part of the transaction, so that the cursor is not
                      // persisted (yet)
                      int fetchedRows =
                          fetchChunk(fetchSQL, extractor, timerSample, timer, isFirstRow);

                      if (fetchedRows < props.getChunkSize()) {
                        // we had rows, but less than allowed, indicating we're done

                        if (fetchedRows == 0) {
                          log.trace("{} catchup {}, empty cursor", req, phase);
                          return null; // signal, nothing was read
                        } else {
                          log.trace(
                              "{} catchup {}, quick catchup: no need to hold the cursor",
                              req,
                              phase);
                          return false;
                        }

                        /*
                         * There is no need to rollback the transaction if the cursor is exhausted:
                         *
                         * <pre>
                         * postgres=# begin; declare x cursor with hold for select generate_series(1,100000000);
                         * postgres=*# commit ;
                         * Time: 5810.979 ms (00:05.811)
                         *
                         * postgres=# close x;
                         * Time: 67.996 ms
                         * </pre>
                         *
                         * compared to
                         *
                         * <pre>
                         * postgres=# begin; declare x cursor with hold for select generate_series(1,100000000);
                         * Time: 0.428 ms
                         *
                         * postgres=*# fetch absolute 99999999 x;
                         * Time: 1957.552 ms (00:01.958)
                         *
                         * postgres=*# commit ;
                         * Time: 0.375 ms
                         *
                         * postgres=# close x;
                         * Time: 0.165 ms
                         * </pre>
                         */
                      }

                      return true;
                    } catch (SQLException sql) {
                      throw new TransactionException("While catching up:", sql) {};
                    }
                  });

      if (Boolean.TRUE.equals(continueFetching)) {
        continueFetchingUntilExhausted(fetchSQL, extractor, timerSample, timer, isFirstRow);
        return true;
      } else {
        // null indicates there were no rows at all
        return (continueFetching != null);
      }
    } catch (TransactionException e) {
      if (e.getCause() instanceof SQLException sql) {
        // unwrap
        throw sql;
      } else {
        throw e;
      }
    } finally {
      closeCursor(cursorName);
      statementHolder.clear();
    }
  }

  private void continueFetchingUntilExhausted(
      String fetchSQL,
      PgFactExtractor extractor,
      Timer.Sample timerSample,
      Timer timer,
      AtomicBoolean isFirstRow) {
    log.trace("{} catchup {}, fetching further rows from held cursor", req, phase);

    while (!statementHolder.wasCanceled()) {
      try {
        if (fetchChunk(fetchSQL, extractor, timerSample, timer, isFirstRow) < props.getChunkSize())
          // early exit, as there are no more rows to fetch
          return;
      } catch (SQLException e) {
        // matrushka exception
        throw new TransactionException("While fetching: ", e) {};
      }
    }
  }

  @VisibleForTesting
  void closeCursor(String cursorName) {
    try {
      ds.getConnection().prepareStatement(createCloseCursorSql(cursorName)).execute();
    } catch (Exception e) {
      // swallow as unimportant to not break the control flow
      log.warn("{} catchup {}, While closing held cursor", req, phase, e);
    }
  }

  @VisibleForTesting
  void declareCursor(
      @NonNull Connection connection,
      String cursorName,
      PgQueryBuilder queryBuilder,
      AtomicLong fromSerial)
      throws SQLException {

    log.trace(
        "{} catchup {}, declaring cursor-with-hold after SER={}", req, phase, fromSerial.get());

    // ok, this is messy, but the only way i can think of to still use the preparedQuerySetter,
    // which is impossible to do, if we pass a string to a function...

    String sql =
        String.format(
            """
    DECLARE %s CURSOR WITH HOLD FOR WITH numbered AS materialized
    (
    SELECT ser,
           ((row_number() OVER (ORDER BY ser ASC) - 1) / %s ) AS grp,
           row_number() OVER (ORDER BY ser ASC) AS rn
           FROM ( %s ) AS sub
    )
    SELECT array_agg(ser ORDER BY rn) FROM numbered GROUP BY grp ORDER BY grp ASC
""",
            cursorName, props.getChunkSize(), queryBuilder.createSQL());

    try (PreparedStatement declare = connection.prepareStatement(sql)) {
      statementHolder.statement(declare, true);
      queryBuilder.createStatementSetter(fromSerial).setValues(declare);
      declare.execute();
      log.trace("{} catchup {}, cursor-with-hold declared", req, phase);
    }
  }

  @VisibleForTesting
  int fetchChunk(
      @NonNull String fetchSQL,
      @NonNull PgFactExtractor extractor,
      @NonNull Timer.Sample timerSample,
      @NonNull Timer timer,
      @NonNull AtomicBoolean firstRowSeen)
      throws SQLException {
    int rows = 0;
    try (Statement fetch = ds.getConnection().createStatement()) {
      fetch.setFetchSize(props.getPageSize());
      statementHolder.statement(fetch, false);
      try (ResultSet rs = fetch.executeQuery(fetchSQL)) {
        if (firstRowSeen.compareAndSet(false, true)) {
          logIfAboveThreshold(Duration.ofNanos(timerSample.stop(timer)));
        }
        while (rs.next()) {
          if (statementHolder.wasCanceled() || rs.isClosed()) {
            return rows;
          }

          try {
            PgFact fact = extractor.mapRow(rs, rows);
            pipeline.process(Signal.of(fact));
            rows++;
          } catch (PSQLException e) {
            if (statementHolder.wasCanceled()) {
              log.trace("fetch chunk statement was cancelled", e);
              return rows;
            } else {
              throw e;
            }
          }
        }
        return rows;
      }
    } catch (PSQLException e) {
      if (statementHolder.wasCanceled()) {
        log.trace("fetch chunk statement was cancelled", e);
        return rows;
      } else {
        throw e;
      }
    }
  }

  @VisibleForTesting
  String createFetchSql(@NonNull String cursorName) {
    return "SELECT " + PgConstants.PROJECTION_FACT + " FROM fetchFactsFrom('" + cursorName + "')";
  }

  @VisibleForTesting
  @NonNull
  String createCloseCursorSql(@NonNull String cursorName) {
    return "CLOSE " + cursorName;
  }

  @VisibleForTesting
  @NonNull
  String createCursorName() {
    return "catchup_" + UUID.randomUUID().toString().replace("-", "");
  }

  void logIfAboveThreshold(Duration elapsed) {
    if (elapsed.compareTo(FIRST_ROW_FETCHING_THRESHOLD) > 0) {
      log.info(
          "{} catchup took {}ms until the held cursor returned the first result",
          req,
          elapsed.toMillis());
    }
  }
}
