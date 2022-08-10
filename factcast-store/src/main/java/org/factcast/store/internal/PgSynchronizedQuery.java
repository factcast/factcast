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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.Fact;
import org.factcast.core.subscription.SubscriptionImpl;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.store.internal.query.PgLatestSerialFetcher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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
@RequiredArgsConstructor
@Slf4j
class PgSynchronizedQuery {

  @NonNull final JdbcTemplate jdbcTemplate;

  @NonNull final String sql;

  @NonNull final PreparedStatementSetter setter;

  @NonNull final RowCallbackHandler rowHandler;

  @NonNull final TransactionTemplate transactionTemplate;

  @NonNull final AtomicLong serialToContinueFrom;

  @NonNull final PgLatestSerialFetcher latestFetcher;

  PgSynchronizedQuery(
      @NonNull JdbcTemplate jdbcTemplate,
      @NonNull String sql,
      @NonNull PreparedStatementSetter setter,
      @NonNull RowCallbackHandler rowHandler,
      @NonNull AtomicLong serialToContinueFrom,
      @NonNull PgLatestSerialFetcher fetcher) {
    this.serialToContinueFrom = serialToContinueFrom;
    latestFetcher = fetcher;
    this.jdbcTemplate = jdbcTemplate;
    this.sql = sql;
    this.setter = setter;
    this.rowHandler = rowHandler;

    // noinspection ConstantConditions
    DataSourceTransactionManager transactionManager =
        new DataSourceTransactionManager(jdbcTemplate.getDataSource());
    transactionTemplate = new TransactionTemplate(transactionManager);
  }

  // the synchronized here is crucial!
  @SuppressWarnings("SameReturnValue")
  public synchronized void run(boolean useIndex) {
    // TODO recheck latest handling - looks b0rken
    long latest = latestFetcher.retrieveLatestSer();
    transactionTemplate.execute(
        status -> {
          if (!useIndex) {
            jdbcTemplate.execute("SET LOCAL enable_bitmapscan=0;");
          }
          jdbcTemplate.query(sql, setter, rowHandler);
          return null;
        });
    // shift to max(retrievedLatestSer, and ser as updated in
    // rowHandler)
    serialToContinueFrom.set(Math.max(latest, serialToContinueFrom.get()));
  }

  @RequiredArgsConstructor
  static class FactRowCallbackHandler implements RowCallbackHandler {

    final SubscriptionImpl subscription;

    final FactInterceptor interceptor;

    final Supplier<Boolean> isConnectedSupplier;

    final AtomicLong serial;

    final SubscriptionRequestTO request;

    @SuppressWarnings("NullableProblems")
    @Override
    public void processRow(ResultSet rs) throws SQLException {
      if (Boolean.TRUE.equals(isConnectedSupplier.get())) {
        if (rs.isClosed()) {
          throw new IllegalStateException(
              "ResultSet already closed. We should not have got here. THIS IS A BUG!");
        }
        Fact f = PgFact.from(rs);
        try {
          interceptor.accept(f);
          log.trace("{} notifyElement called with id={}", request, f.id());
          serial.set(rs.getLong(PgConstants.COLUMN_SER));
        } catch (Throwable e) {
          rs.close();
          log.warn("{} notifyError called with id={}", request, f.id());
          subscription.notifyError(e);
        }
      }
    }
  }
}
