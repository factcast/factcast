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
import java.sql.*;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.*;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.store.StoreConfigurationProperties;
import org.factcast.store.internal.PgFact;
import org.factcast.store.internal.PgMetrics;
import org.factcast.store.internal.StoreMetrics;
import org.factcast.store.internal.catchup.AbstractPgCatchup;
import org.factcast.store.internal.catchup.PgCatchupFactory;
import org.factcast.store.internal.catchup.tools.fetching.FetchingQuery;
import org.factcast.store.internal.pipeline.*;
import org.factcast.store.internal.query.PgQueryBuilder;
import org.factcast.store.internal.rowmapper.PgFactExtractor;
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
      @NonNull SingleConnectionDataSource ds,
      PgCatchupFactory.@NonNull Phase phase) {
    super(props, metrics, req, pipeline, serial, ds, phase);
  }

  @SneakyThrows
  @Override
  public void run() {
    try {

      final var b = createPgQueryBuilder(req.specs());
      final var extractor = new PgFactExtractor(serial);
      final var fromSerial = serial.get() < fastForward ? new AtomicLong(fastForward) : serial;
      final var catchupSQL = b.createSQL();
      final var isFromScratch = (fromSerial.get() <= 0);
      log.trace("{} catchup {} - facts starting with SER={}", req, phase, fromSerial.get());

      try (Connection conn = ds.getConnection();
          PreparedStatement prep = conn.prepareStatement(catchupSQL); ) {
        // this needs to be transactional for fetch-size to have any effect whatsoever.
        conn.setAutoCommit(false);
        prep.setFetchSize(props.getPageSize());
        prep.setQueryTimeout(0);
        b.createStatementSetter(fromSerial).setValues(prep);

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
      log.trace("Done fetching, flushing.");
      pipeline.process(Signal.flush());
    }
  }

  /** hook for tests to influence the generated sql */
  @VisibleForTesting
  protected PgQueryBuilder createPgQueryBuilder(List<FactSpec> specs) {
    return new PgQueryBuilder(req.specs());
  }

  private void logIfAboveThreshold(Duration elapsed) {
    if (elapsed.compareTo(FIRST_ROW_FETCHING_THRESHOLD) > 0) {
      log.info("{} catchup - took {}s to stream the first result set", req, elapsed.toSeconds());
    }
  }

  @VisibleForTesting
  protected RowCallbackHandler createRowCallbackHandler(PgFactExtractor extractor) {
    // as we cannot call rs.isCLosed or close, we need to have a separate flag
    AtomicBoolean closed = new AtomicBoolean(false);

    return rs -> {
      try {
        if (closed.get()) return;

        // this might still throw a SQLException "already closed" due to bad timing.
        PgFact f = extractor.mapRow(rs, 0);

        pipeline.process(Signal.of(f));
      } catch (PipelineAlreadyClosedException e) {
        log.trace("{} catchup {} - pipeline was closed, exiting.", req, phase);
        closed.set(true);
      }
    };
  }
}
