/*
 * Copyright © 2017-2022 factcast.org
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
package org.factcast.store.internal.query;

import com.google.common.annotations.VisibleForTesting;
import java.sql.*;
import java.util.concurrent.atomic.*;
import lombok.*;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.*;
import org.springframework.lang.Nullable;

/**
 * Holder for a current statement. This is used to cancel the statement in case of a timeout or to
 * know if it was canceled from another thread. Note that this is not a singleton, but scoped to a
 * PGFactStream execution.
 */
@Slf4j
public class CurrentStatementHolder {

  public void close() {
    // if we still have a statement, we need to cancel it
    Statement st = statement.get();
    if (st != null) {
      cancel();
    }
    // otherwise, there is nothing to do. Actually, it would be the expected state and behavior.
  }

  @VisibleForTesting
  void clear() {
    statement.set(null);
  }

  @RequiredArgsConstructor
  class ContextPreparedStatement implements PreparedStatement {
    @Delegate final PreparedStatement delegate;

    @Override
    public void close() throws SQLException {
      try {
        delegate.close();
      } finally {
        CurrentStatementHolder.this.clear();
      }
    }

    @Override
    public void closeOnCompletion() throws SQLException {
      try {
        delegate.closeOnCompletion();
      } finally {
        CurrentStatementHolder.this.clear();
      }
    }
  }

  public PreparedStatement prepareStatement(@NonNull Connection con, @NonNull String sql)
      throws SQLException {
    PreparedStatement preparedStatement = con.prepareStatement(sql);
    statement(preparedStatement);
    return register(preparedStatement);
  }

  public PreparedStatement prepareStatement(
      @NonNull Connection con, @NonNull String sql, @NonNull PreparedStatementSetter setter)
      throws SQLException {
    PreparedStatement preparedStatement = prepareStatement(con, sql);
    setter.setValues(preparedStatement);
    return preparedStatement;
  }

  public PreparedStatement register(@NonNull PreparedStatement preparedStatement) {
    statement.set(preparedStatement);
    if (preparedStatement instanceof ContextPreparedStatement)
      log.warn("Double wrapping of ContextPreparedStatement prevented. This is a bug.");
    return new ContextPreparedStatement(preparedStatement);
  }

  private final AtomicReference<Statement> statement = new AtomicReference<>();
  private final AtomicBoolean wasCanceled = new AtomicBoolean(false);

  public void cancel() {
    Statement st = statement.get();
    if (st != null) {
      // not elegant, but plenty of different things can go wrong
      try {
        Connection c = getConnectionFrom(st);
        cancelStatement(st);
        tryRollback(c);
      } finally {
        clear();
      }
    } else {
      log.trace("Statement not set, so no canceling necessary. This is a bug.");
    }
  }

  @VisibleForTesting
  void tryRollback(@Nullable Connection c) {
    if (c != null)
      try {
        if (!c.getAutoCommit()) {
          // we have to roll back the tx on the underlying connection
          // if we do not end the transaction, statements are canceled but still "idle in
          // transaction" and so block further actions like wiping between tests
          c.rollback();
        }
      } catch (SQLException e) {
        log.debug(
            "Exception while rolling back transaction for cancelled statement {}:", statement, e);
      } finally {
        tryClose(c);
      }
  }

  @VisibleForTesting
  static void tryClose(@NonNull Connection c) {
    try {
      c.close();
    } catch (SQLException e) {
      log.debug("Exception while closing connection {}:", c, e);
    }
  }

  @VisibleForTesting
  void cancelStatement(@NonNull Statement st) {
    log.info("Canceling statement {}", st);
    try {
      st.cancel();
      st.close();
    } catch (SQLException e) {
      log.debug("Exception while cancelling statement {}:", statement, e);
    } finally {
      wasCanceled.set(true);
    }
  }

  @Nullable
  @VisibleForTesting
  Connection getConnectionFrom(Statement st) {
    Connection c = null;
    try {
      c = st.getConnection();
    } catch (SQLException e) {
      log.debug("While fetching connection from statement to cancel: {}", statement, e);
    }
    return c;
  }

  @Nullable
  public Statement statement() {
    return statement.get();
  }

  @VisibleForTesting
  void statement(@NonNull Statement statement) {
    if (this.statement.getAndSet(statement) != null) {
      log.warn("Overwriting a running statement? This is a bug.");
    }

    if (wasCanceled.get()) {
      log.warn("Statement was already canceled, compensating. This is a bug.");
      wasCanceled.set(false);
    }

    this.statement.set(statement);
  }

  public boolean wasCanceled() {
    return this.wasCanceled.get();
  }
}
