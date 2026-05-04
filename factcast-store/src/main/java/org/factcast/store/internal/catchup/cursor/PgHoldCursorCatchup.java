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
package org.factcast.store.internal.catchup.cursor;

import com.google.common.annotations.VisibleForTesting;
import io.micrometer.core.instrument.Timer;
import java.sql.*;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import javax.sql.DataSource;
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
import org.factcast.store.internal.pipeline.ServerPipeline;
import org.factcast.store.internal.pipeline.Signal;
import org.factcast.store.internal.query.CurrentStatementHolder;
import org.factcast.store.internal.query.PgQueryBuilder;
import org.factcast.store.internal.rowmapper.PgFactExtractor;
import org.postgresql.util.PSQLException;
import org.springframework.jdbc.core.PreparedStatementSetter;

@Slf4j
public class PgHoldCursorCatchup extends AbstractPgCatchup {

  @SuppressWarnings("java:S107")
  public PgHoldCursorCatchup(
      @NonNull StoreConfigurationProperties props,
      @NonNull PgMetrics metrics,
      @NonNull SubscriptionRequestTO req,
      @NonNull ServerPipeline pipeline,
      @NonNull AtomicLong serial,
      @NonNull CurrentStatementHolder statementHolder,
      @NonNull DataSource ds,
      PgCatchupFactory.@NonNull Phase phase) {
    super(props, metrics, req, pipeline, serial, statementHolder, ds, phase);
  }

  @SneakyThrows
  @Override
  public void run() {
    final var connection = ds.getConnection();
    final var cursorName = createCursorName();
    try {
      fetch(connection, cursorName);
    } finally {
      closeCursorQuietly(connection, cursorName);
      statementHolder.clear();

      log.trace("Done fetching, flushing.");
      pipeline.process(Signal.flush());
    }
  }

  @VisibleForTesting
  void fetch(Connection connection, String cursorName) throws SQLException {
    final var queryBuilder = new PgQueryBuilder(req.specs(), statementHolder);
    final var extractor = new PgFactExtractor(serial);
    final var fromSerial = new AtomicLong(Math.max(serial.get(), fastForward));
    final var catchupSQL = queryBuilder.createSQL();
    final var declareCursorSQL = createDeclareCursorWithHoldSql(cursorName, catchupSQL);
    final var fetchSQL = createFetchSql(cursorName);
    final var isFromScratch = (fromSerial.get() <= 0);
    final var timer = metrics.timer(StoreMetrics.OP.RESULT_STREAM_START, isFromScratch);
    final var timerSample = metrics.startSample();
    final var isFirstRow = new AtomicBoolean(false);

    log.trace(
        "{} catchup {}, declaring cursor-with-hold after SER={}", req, phase, fromSerial.get());

    declareCursor(connection, declareCursorSQL, queryBuilder.createStatementSetter(fromSerial));
    connection.commit();
    connection.setAutoCommit(true);
    try {
      while (!statementHolder.wasCanceled()) {
        int fetchedRows =
            fetchChunk(connection, fetchSQL, extractor, timerSample, timer, isFirstRow);

        if (fetchedRows == 0) {
          if (!isFirstRow.get()) {
            logIfAboveThreshold(Duration.ofNanos(timerSample.stop(timer)));
          }
          log.trace("{} catchup {}, no more rows in held cursor", req, phase);
          return;
        }
      }
    } finally {
      connection.setAutoCommit(false);
    }
  }

  @VisibleForTesting
  void declareCursor(
      @NonNull Connection connection,
      @NonNull String declareSQL,
      @NonNull PreparedStatementSetter statementSetter)
      throws SQLException {
    try (PreparedStatement declare = connection.prepareStatement(declareSQL)) {
      statementHolder.statement(declare, true);
      statementSetter.setValues(declare);
      declare.execute();
    }
  }

  @VisibleForTesting
  int fetchChunk(
      @NonNull Connection connection,
      @NonNull String fetchSQL,
      @NonNull PgFactExtractor extractor,
      @NonNull Timer.Sample timerSample,
      @NonNull Timer timer,
      @NonNull AtomicBoolean firstRowSeen)
      throws SQLException {
    int rows = 0;
    try (Statement fetch = connection.createStatement()) {
      fetch.setFetchSize(props.getPageSize());
      statementHolder.statement(fetch, false);
      try (ResultSet rs = fetch.executeQuery(fetchSQL)) {
        while (rs.next()) {
          if (statementHolder.wasCanceled() || rs.isClosed()) {
            return rows;
          }

          if (firstRowSeen.compareAndSet(false, true)) {
            logIfAboveThreshold(Duration.ofNanos(timerSample.stop(timer)));
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
  String createDeclareCursorWithHoldSql(@NonNull String cursorName, @NonNull String query) {
    return "DECLARE " + cursorName + " NO SCROLL CURSOR WITH HOLD FOR " + query;
  }

  @VisibleForTesting
  String createFetchSql(@NonNull String cursorName) {
    return "FETCH FORWARD " + props.getPageSize() + " FROM " + cursorName;
  }

  @VisibleForTesting
  String createCloseCursorSql(@NonNull String cursorName) {
    return "CLOSE " + cursorName;
  }

  @VisibleForTesting
  String createCursorName() {
    return "catchup_" + UUID.randomUUID().toString().replace("-", "");
  }

  @VisibleForTesting
  void closeCursorQuietly(@NonNull Connection connection, @NonNull String cursorName) {
    try (Statement close = connection.createStatement()) {
      close.execute(createCloseCursorSql(cursorName));
    } catch (Exception e) {
      log.trace("{} catchup {} while closing held cursor {}", req, phase, cursorName, e);
    }
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
