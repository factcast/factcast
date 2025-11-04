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
package org.factcast.store.internal.catchup.fetching;

import com.google.common.annotations.VisibleForTesting;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.concurrent.atomic.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.store.StoreConfigurationProperties;
import org.factcast.store.internal.PgFact;
import org.factcast.store.internal.PgMetrics;
import org.factcast.store.internal.StoreMetrics;
import org.factcast.store.internal.catchup.PgCatchup;
import org.factcast.store.internal.catchup.PgCatchupFactory;
import org.factcast.store.internal.listen.*;
import org.factcast.store.internal.pipeline.ServerPipeline;
import org.factcast.store.internal.pipeline.Signal;
import org.factcast.store.internal.query.CurrentStatementHolder;
import org.factcast.store.internal.query.PgQueryBuilder;
import org.factcast.store.internal.rowmapper.PgFactExtractor;
import org.postgresql.util.PSQLException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;

@Slf4j
@RequiredArgsConstructor
public class PgFetchingCatchup implements PgCatchup {

  static final Duration FIRST_ROW_FETCHING_THRESHOLD = Duration.ofSeconds(1);

  @NonNull final PgConnectionSupplier connectionSupplier;

  @NonNull final StoreConfigurationProperties props;

  @NonNull final PgMetrics metrics;

  @NonNull final SubscriptionRequestTO req;

  @NonNull final ServerPipeline pipeline;

  @NonNull final AtomicLong serial;

  @NonNull final CurrentStatementHolder statementHolder;

  @NonNull final PgCatchupFactory.Phase phase;

  long fastForward = 0;

  @SneakyThrows
  @Override
  public void run() {
    try (var ds =
        connectionSupplier.getPooledAsSingleDataSource(
            ConnectionModifier.withAutoCommitDisabled(),
            ConnectionModifier.withApplicationName(req.debugInfo()))) {
      var jdbc = new JdbcTemplate(ds);
      fetch(jdbc);
    } finally {
      statementHolder.clear();

      log.trace("Done fetching, flushing.");
      pipeline.process(Signal.flush());
    }
  }

  @VisibleForTesting
  void fetch(JdbcTemplate jdbc) {
    jdbc.setFetchSize(props.getPageSize());
    jdbc.setQueryTimeout(0); // disable query timeout
    final var b = new PgQueryBuilder(req.specs(), statementHolder);
    final var extractor = new PgFactExtractor(serial);
    final var catchupSQL = b.createSQL();
    final var fromSerial = serial.get() < fastForward ? new AtomicLong(fastForward) : serial;
    final var isFromScratch = (fromSerial.get() <= 0);
    final var timer = metrics.timer(StoreMetrics.OP.CATCHUP_STREAM_START, isFromScratch);
    final var rowCallbackHandler = createTimedRowCallbackHandler(extractor, timer);
    log.trace("{} catchup {} - facts starting with SER={}", req, phase, fromSerial.get());
    jdbc.query(catchupSQL, b.createStatementSetter(fromSerial), rowCallbackHandler);
  }

  @VisibleForTesting
  RowCallbackHandler createTimedRowCallbackHandler(PgFactExtractor extractor, Timer timer) {
    final var isFirstRow = new AtomicBoolean(true);
    final var timerSample = metrics.startSample();
    final var handler = createRowCallbackHandler(extractor);
    return rs -> {
      if (isFirstRow.getAndSet(false)) {
        final var elapsed = Duration.ofNanos(timerSample.stop(timer));
        logIfAboveThreshold(elapsed);
      }
      handler.processRow(rs);
    };
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

  @Override
  public void fastForward(long serialToStartFrom) {
    this.fastForward = serialToStartFrom;
  }
}
