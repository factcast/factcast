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
import com.google.common.base.Preconditions;
import java.sql.*;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.*;
import lombok.*;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.store.StoreConfigurationProperties;
import org.factcast.store.internal.*;
import org.factcast.store.internal.catchup.*;
import org.factcast.store.internal.pipeline.*;
import org.factcast.store.internal.query.*;
import org.factcast.store.internal.rowmapper.PgFactExtractor;
import org.postgresql.util.PSQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.*;

public class PgChunkedWithHoldCursorCatchup extends AbstractPgCatchup {

  private static final Logger log = LoggerFactory.getLogger(PgChunkedWithHoldCursorCatchup.class);
  private Connection connection;

  @SneakyThrows
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
    connection = ds.getConnection();
  }

  @SneakyThrows
  @Override
  public synchronized void run() {
    if (connection == null)
      throw new IllegalStateException("PgChunkedWithHoldCursorCatchup is a one-shot object");

    try (Cursor cursor = createCursor(props.getChunkSize())) {
      if (fetchAll(cursor)) {
        log.trace("Done fetching, flushing.");
        pipeline.process(Signal.flush());
      }
    } finally {
      connection.close();
      connection = null;
    }
  }

  // needed for comfortable mocking
  @VisibleForTesting
  @NonNull
  Cursor createCursor(int chunkSize) throws SQLException {
    return new Cursor(chunkSize);
  }

  void logIfAboveThreshold(Logger log, Duration elapsed) {
    if (elapsed.compareTo(FIRST_ROW_FETCHING_THRESHOLD) > 0) {
      log.info(
          "{} catchup took {}ms until the held cursor (not yet committed) returned the first result",
          req,
          elapsed.toMillis());
    }
  }

  /** returns true if fetch was not completely empty */
  @VisibleForTesting
  boolean fetchAll(@NonNull Cursor cursor) throws SQLException {
    final var queryBuilder = new PgQueryBuilder(req.specs(), statementHolder);
    queryBuilder.serialsOnly();

    final var extractor = new PgFactExtractor(serial);
    final var fromSerial = new AtomicLong(Math.max(serial.get(), fastForward));

    if (statementHolder.wasCanceled()) return false;

    try {
      Boolean moreToFetch =
          inTransaction(() -> declareAndFetchFirst(cursor, queryBuilder, fromSerial, extractor));

      if (moreToFetch == null) {
        // no rows fetched whatsoever
        return false;
      } else {
        if (moreToFetch) {
          log.trace("{} catchup {}, fetching further rows from held cursor", req, phase);

          // thing is, we still need another transaction around, so that setFetchSize continues to
          // work
          //
          // also we want to keep the transaction boundaries to one chunk only, in order not to
          // block the
          // index maintenance on the fact table
          inTransaction(() -> continueFetchingUntilExhausted(cursor, extractor));
        }
        return true;
      }
    } finally {
      statementHolder.clear();
    }
  }

  @VisibleForTesting
  protected void continueFetchingUntilExhausted(
      @NonNull Cursor cursor, @NonNull PgFactExtractor extractor) throws SQLException {

    Preconditions.checkArgument(
        !connection.getAutoCommit(), "We rely on this being executed in a transaction");

    while (!statementHolder.wasCanceled()) {
      log.debug("{} catchup {}, fetching next chunk", req, phase);
      if (cursor.fetchChunk(extractor) < cursor.chunkSize())
        // early exit, as there are no more rows to fetch
        return;
    }
  }

  /**
   * @return true if there is more to fetch, <br>
   *     false if some rows were fetched, but the cursor is exhausted now, so there is nothing more
   *     to fetch, <br>
   *     null if no rows were fetched at all
   * @throws SQLException
   */
  @VisibleForTesting
  @Nullable
  Boolean declareAndFetchFirst(
      @NonNull Cursor cursor,
      @NonNull PgQueryBuilder queryBuilder,
      @org.jspecify.annotations.NonNull AtomicLong fromSerial,
      @org.jspecify.annotations.NonNull PgFactExtractor extractor)
      throws SQLException {

    Preconditions.checkArgument(
        !connection.getAutoCommit(), "We rely on this being executed in a transaction");

    final var timer = metrics.timer(StoreMetrics.OP.RESULT_STREAM_START, fromSerial.get() <= 0);
    final var timerSample = metrics.startSample();

    cursor.declare(queryBuilder, fromSerial);

    // as declaring the cursor could have taken some time, we'll check again
    if (statementHolder.wasCanceled()) return null;

    log.debug("{} catchup {}, fetching first chunk", req, phase);

    // the first fetch is part of the transaction, so that the cursor is not
    // persisted (yet)
    int fetchedRows =
        cursor.fetchChunk(
            extractor, () -> logIfAboveThreshold(log, Duration.ofNanos(timerSample.stop(timer))));

    if (fetchedRows < props.getChunkSize()) {
      // we had rows, but less than allowed, indicating we're done

      if (fetchedRows == 0) {
        log.trace("{} catchup {}, empty cursor", req, phase);
        return null;
      } else {
        // There is no need to rollback the transaction if the cursor is
        // exhausted.
        log.trace("{} catchup {}, quick catchup: no need to hold the cursor", req, phase);
        return false;
      }
    } else return true;
  }

  @VisibleForTesting
  protected class Cursor implements AutoCloseable {
    @Getter private final int chunkSize;
    @Getter private final String name = "catchup_" + UUID.randomUUID().toString().replace("-", "");
    private final @NonNull String fetchSql;

    public Cursor(int chunkSize) {
      this.chunkSize = chunkSize;
      fetchSql = createFetchSQL();
    }

    @SuppressWarnings("java:S2077")
    @VisibleForTesting
    public void close() {
      try (PreparedStatement ps = connection.prepareStatement("CLOSE " + name); ) {
        ps.execute();
      } catch (Exception e) {
        // swallow as unimportant to not break the control flow
        log.warn("{} catchup {}, While closing held cursor", req, phase, e);
      }
    }

    @VisibleForTesting
    void declare(@NonNull PgQueryBuilder queryBuilder, @NonNull AtomicLong fromSerial)
        throws SQLException {

      Preconditions.checkArgument(chunkSize >= 1000, "chunkSize must be >= 1000");

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
              name(), chunkSize, queryBuilder.createSQL());

      try (PreparedStatement declare = connection.prepareStatement(sql)) {
        queryBuilder.createStatementSetter(fromSerial).setValues(declare);
        declare.execute();
        log.trace("{} catchup {}, cursor-with-hold declared", req, phase);
      }
    }

    @VisibleForTesting
    int fetchChunk(@NonNull PgFactExtractor extractor) throws SQLException {
      return fetchChunk(extractor, null);
    }

    @VisibleForTesting
    @SuppressWarnings("java:S1141")
    int fetchChunk(@NonNull PgFactExtractor extractor, @Nullable Runnable callbackAfterExecution)
        throws SQLException {
      @SuppressWarnings("ReassignedVariable")
      int rows = 0;
      try (Statement fetch = connection.createStatement()) {
        fetch.setFetchSize(props.getPageSize());
        statementHolder.statement(fetch);

        try (ResultSet rs = fetch.executeQuery(fetchSql)) {

          if (callbackAfterExecution != null) callbackAfterExecution.run();

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

    private @NonNull String createFetchSQL() {
      return "SELECT " + PgConstants.PROJECTION_FACT + " FROM fetchFactsFrom('" + name() + "')";
    }
  }

  interface ThrowingCallable<R> {
    R call() throws SQLException;
  }

  interface ThrowingRunnable {
    void run() throws SQLException;
  }

  void inTransaction(@Nonnull ThrowingRunnable runnable) throws SQLException {
    doInTransaction(
        () -> {
          runnable.run();
          return null;
        });
  }

  <R> R inTransaction(@Nonnull ThrowingCallable<R> callable) throws SQLException {
    return doInTransaction(callable);
  }

  private <R> R doInTransaction(@NonNull ThrowingCallable<R> callable) throws SQLException {
    connection.setAutoCommit(false);

    try {
      R call = callable.call();
      connection.commit();
      return call;
    } catch (SQLException sql) {
      connection.rollback();
      throw sql;
    }
  }
}
