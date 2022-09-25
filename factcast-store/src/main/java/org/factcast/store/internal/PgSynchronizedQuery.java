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
import org.factcast.store.internal.query.CurrentStatementHolder;
import org.factcast.store.internal.query.PgLatestSerialFetcher;
import org.postgresql.util.PSQLException;
import org.springframework.dao.DataAccessException;
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

  @NonNull final CurrentStatementHolder statementHolder;

  PgSynchronizedQuery(
      @NonNull JdbcTemplate jdbcTemplate,
      @NonNull String sql,
      @NonNull PreparedStatementSetter setter,
      @NonNull RowCallbackHandler rowHandler,
      @NonNull AtomicLong serialToContinueFrom,
      @NonNull PgLatestSerialFetcher fetcher,
      @NonNull CurrentStatementHolder statementHolder) {
    this.serialToContinueFrom = serialToContinueFrom;
    latestFetcher = fetcher;
    this.jdbcTemplate = jdbcTemplate;
    this.sql = sql;
    this.setter = setter;
    this.rowHandler = rowHandler;
    this.statementHolder = statementHolder;

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
    try {
      transactionTemplate.execute(
          status -> {
            if (!useIndex) {
              jdbcTemplate.execute("SET LOCAL enable_bitmapscan=0;");
            }

            jdbcTemplate.query(
                sql,
                ps -> {
                  statementHolder.statement(ps);
                  setter.setValues(ps);
                },
                rowHandler);
            return null;
          });

      // shift to max(retrievedLatestSer, and ser as updated in
      // rowHandler)
      serialToContinueFrom.set(Math.max(latest, serialToContinueFrom.get()));
    } catch (DataAccessException e) {
      // #2165 swallow exception after cancel.
      if (statementHolder.wasCanceled()) {
        log.trace("Query was cancelled during execution", e);
      } else throw e;
    }
  }

  @RequiredArgsConstructor
  public static class FactRowCallbackHandler implements RowCallbackHandler {

    final SubscriptionImpl subscription;

    final FactInterceptor interceptor;

    final Supplier<Boolean> isConnectedSupplier;

    final AtomicLong serial;

    final SubscriptionRequestTO request;

    final CurrentStatementHolder statementHolder;

    @SuppressWarnings("NullableProblems")
    @Override
    public void processRow(ResultSet rs) throws SQLException {
      if (Boolean.TRUE.equals(isConnectedSupplier.get())) {
        if (rs.isClosed()) {
          if (!statementHolder.wasCanceled())
            throw new IllegalStateException(
                "ResultSet already closed. We should not have got here. THIS IS A BUG!");
          else return;
        }
        Fact f = null;
        try {
          f = PgFact.from(rs);
          interceptor.accept(f);
          log.trace("{} notifyElement called with id={}", request, f.id());
          serial.set(rs.getLong(PgConstants.COLUMN_SER));
        } catch (PSQLException psql) {
          // see #2088
          if (statementHolder.wasCanceled()) {
            // then we just swallow the exception
            log.trace("Swallowing because statement was cancelled", psql);
          } else escalateError(rs, f, psql);
        } catch (Throwable e) {
          escalateError(rs, f, e);
        }
      }
    }

    private void escalateError(ResultSet rs, Fact f, Throwable e) throws SQLException {
      log.warn("{} notifyError called with id={}", request, f != null ? f.id() : "unknown");
      try {
        rs.close();
      } catch (Throwable ignore) {
      }
      subscription.notifyError(e);
    }
  }
}
