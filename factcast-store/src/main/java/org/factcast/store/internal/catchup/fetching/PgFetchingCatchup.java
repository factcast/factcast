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
import java.util.concurrent.atomic.*;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.Fact;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.store.StoreConfigurationProperties;
import org.factcast.store.internal.catchup.PgCatchup;
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

  @NonNull final PgConnectionSupplier connectionSupplier;

  @NonNull final StoreConfigurationProperties props;

  @NonNull final SubscriptionRequestTO req;

  @NonNull final ServerPipeline pipeline;

  @NonNull final AtomicLong serial;

  @NonNull final CurrentStatementHolder statementHolder;

  @SneakyThrows
  @Override
  public void run() {
    try (var ds =
        connectionSupplier.getPooledAsSingleDataSource(
            ConnectionModifier.withAutoCommitDisabled(),
            ConnectionModifier.withApplicationName(req.debugInfo())); ) {
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
    PgQueryBuilder b = new PgQueryBuilder(req.specs(), statementHolder);
    var extractor = new PgFactExtractor(serial);
    String catchupSQL = b.createSQL(null);
    jdbc.query(
        catchupSQL, b.createStatementSetter(serial, null), createRowCallbackHandler(extractor));
  }

  @VisibleForTesting
  RowCallbackHandler createRowCallbackHandler(PgFactExtractor extractor) {
    return rs -> {
      try {
        if (statementHolder.wasCanceled() || rs.isClosed()) {
          return;
        }

        Fact f = extractor.mapRow(rs, 0);
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
