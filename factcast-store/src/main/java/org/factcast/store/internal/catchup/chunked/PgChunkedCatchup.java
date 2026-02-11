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
package org.factcast.store.internal.catchup.chunked;

import com.google.common.annotations.VisibleForTesting;
import io.micrometer.core.instrument.Timer;
import java.sql.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.*;
import javax.sql.DataSource;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.store.StoreConfigurationProperties;
import org.factcast.store.internal.*;
import org.factcast.store.internal.catchup.*;
import org.factcast.store.internal.pipeline.ServerPipeline;
import org.factcast.store.internal.pipeline.Signal;
import org.factcast.store.internal.query.CurrentStatementHolder;
import org.factcast.store.internal.query.PgQueryBuilder;
import org.factcast.store.internal.rowmapper.PgFactExtractor;
import org.jetbrains.annotations.NotNull;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

@Slf4j
public class PgChunkedCatchup extends AbstractPgCatchup {

  public PgChunkedCatchup(
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
    try {
      fetch(ds);
    } finally {
      statementHolder.clear();

      log.trace("Done fetching, flushing.");
      pipeline.process(Signal.flush());
    }
  }

  @VisibleForTesting
  @SneakyThrows
  @SuppressWarnings("java:S2077")
  void fetch(DataSource ds) {

    try (SingleConnectionDataSource singleConnectionDataSource = createSingleDS(ds); ) {
      JdbcTemplate jdbc = new JdbcTemplate(singleConnectionDataSource);

      String tempTableName = "catchup_" + UUID.randomUUID().toString().replace("-", "");

      try {
        jdbc.setFetchSize(props.getPageSize());
        jdbc.setQueryTimeout(0); // disable query timeout
        if (prepareTemporaryTable(jdbc, tempTableName) > 0) {

          final var extractor = new PgFactExtractor(serial);

          String chunkQuery = prepareChunkQuery(tempTableName);

          int chunkCount = 0;
          int rowsToProcess = -1;
          while (rowsToProcess != 0) {

            if (statementHolder.wasCanceled()) {
              log.trace("{} catchup {} - was cancelled", req, phase);
              return;
            }

            log.trace("{} catchup {} - fetching chunk {}", req, phase, ++chunkCount);
            List<PgFact> facts = jdbc.query(chunkQuery, extractor);
            rowsToProcess = facts.size();
            log.trace(
                "{} catchup {} - processing chunk {} - found {} rows",
                req,
                phase,
                chunkCount,
                rowsToProcess);

            // process them
            facts.forEach(f -> pipeline.process(Signal.of(f)));
          }
          log.trace("{} catchup {} - all chunks processed", req, phase);
        } else {
          log.trace("{} catchup {} - no matching serials found", req, phase);
        }
      } finally {
        // tmp table is not needed anymore. As we reuse the connection, it'd be good to drop it.
        try {

          jdbc.execute("drop table " + tempTableName);
        } catch (Exception e) {
          log.warn("{} catchup {} - while dropping tmp table:", req, phase, e);
        }
      }
    }
  }

  @NotNull
  String prepareChunkQuery(String tempTableName) {
    return """
                with chunk as (
                    with serialsFromTemp as (
                        select ser from $TMP order by ser ASC limit $SIZE
                    )
                    delete from $TMP
                        where ser in (select ser from serialsFromTemp)
                    returning ser
                )
                select $PROJECTION from fact
                where ser in (select ser from chunk)
                order by ser ASC

                """
        // don't want to mess with google formatting
        .replace("$PROJECTION", PgConstants.PROJECTION_FACT)
        .replace("$TMP", tempTableName)
        .replace("$SIZE", Integer.toString(props.getPageSize()));
  }

  int prepareTemporaryTable(JdbcTemplate jdbc, String tempTableName) {
    createTempTable(jdbc, tempTableName);

    final var b = new PgQueryBuilder(req.specs(), statementHolder);
    b.moveSerialsToTempTable(tempTableName);

    final var fromSerial = serial.get() < fastForward ? new AtomicLong(fastForward) : serial;
    final var catchupSQL = b.createSQL(fromSerial.get());
    log.trace("{} catchup {} - facts starting with SER={}", req, phase, fromSerial.get());
    log.trace("{} catchup {} - preparing temp table", req, phase);

    final var isFromScratch = (fromSerial.get() <= 0);
    final var timer = metrics.timer(StoreMetrics.OP.RESULT_STREAM_START, isFromScratch);
    Timer.Sample sample = metrics.startSample();

    int matches = jdbc.update(catchupSQL, b.createStatementSetter());
    log.trace("{} catchup {} - Temp table has {} matching serials", req, phase, matches);
    logIfAboveThreshold(Duration.ofNanos(sample.stop(timer)));
    return matches;
  }

  @SuppressWarnings("java:S2077")
  void createTempTable(JdbcTemplate jdbc, String tempTableName) {
    // the primary key is important here to get a btree for sorting
    jdbc.execute("create temp table " + tempTableName + " (ser bigint primary key)");
  }

  @SneakyThrows
  SingleConnectionDataSource createSingleDS(@NonNull DataSource ds) {
    Connection conn = ds.getConnection();
    return new SingleConnectionDataSource(conn, true) {
      @Override
      public void destroy() {
        super.destroy();
        try {
          conn.close();
        } catch (SQLException e) {
          log.error("Error closing connection", e);
        }
      }
    };
  }

  void logIfAboveThreshold(Duration elapsed) {
    if (elapsed.compareTo(FIRST_ROW_FETCHING_THRESHOLD) > 0) {
      log.info(
          "{} catchup - took {}s to find all the serials matching and store them in temp table",
          req,
          elapsed.toSeconds());
    }
  }
}
