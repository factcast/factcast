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
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.store.StoreConfigurationProperties;
import org.factcast.store.internal.catchup.BufferingFactInterceptor;
import org.factcast.store.internal.catchup.PgCatchup;
import org.factcast.store.internal.listen.PgConnectionSupplier;
import org.factcast.store.internal.query.CurrentStatementHolder;
import org.factcast.store.internal.query.PgQueryBuilder;
import org.factcast.store.internal.rowmapper.PgFactExtractor;
import org.postgresql.jdbc.PgConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

@Slf4j
@RequiredArgsConstructor
public class PgFetchingCatchup implements PgCatchup {

  @NonNull final PgConnectionSupplier connectionSupplier;

  @NonNull final StoreConfigurationProperties props;

  @NonNull final SubscriptionRequestTO req;

  @NonNull final BufferingFactInterceptor interceptor;

  @NonNull final AtomicLong serial;

  @NonNull final CurrentStatementHolder statementHolder;

  @SneakyThrows
  @Override
  public void run() {

    PgConnection connection = connectionSupplier.get();
    connection.setAutoCommit(false); // necessary for using cursors

    // connection may stay open quite a while, and we do not want a CPool to interfere
    SingleConnectionDataSource ds = new SingleConnectionDataSource(connection, true);

    try {
      var jdbc = new JdbcTemplate(ds);
      fetch(jdbc);
    } finally {
      log.trace("Done fetching, flushing interceptor");
      interceptor.flush();
      ds.destroy();
      statementHolder.statement(null);
    }
  }

  @VisibleForTesting
  void fetch(JdbcTemplate jdbc) {
    jdbc.setFetchSize(props.getPageSize());
    jdbc.setQueryTimeout(0); // disable query timeout
    PgQueryBuilder b = new PgQueryBuilder(req.specs(), statementHolder);
    var extractor = new PgFactExtractor(serial);
    String catchupSQL = b.createSQL();
    jdbc.query(catchupSQL, b.createStatementSetter(serial), createRowCallbackHandler(extractor));
  }

  @VisibleForTesting
  RowCallbackHandler createRowCallbackHandler(PgFactExtractor extractor) {
    return rs -> this.interceptor.accept(extractor.mapRow(rs, 0));
  }
}
