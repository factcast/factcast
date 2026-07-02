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

import java.io.Closeable;
import java.sql.*;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CurrentStatementHolder implements Closeable {
  private final Object mutex = new Object();
  private Statement statement;
  private boolean wasCanceled;

  @Override
  public void close() {
    synchronized (mutex) {
      if (statement == null) {
        log.trace("statement is null, so no closing necessary. Duplicate call to close()?");
        return;
      }

      // not elegant, but plenty of different things can go wrong
      Connection c = null;

      try {
        c = statement.getConnection();
      } catch (SQLException e) {
        log.debug("While fetching connection from statement to cancel: {}", statement, e);
      }

      if (statement != null) {
        log.info("Canceling statement {}", statement);
        try {
          statement.cancel();
          statement.close();
        } catch (SQLException e) {
          log.debug("Exception while cancelling statement {}:", statement, e);
        } finally {
          wasCanceled = true;
        }

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
                "Exception while rolling back transaction for cancelled statement {}:",
                statement,
                e);
          }
      }
    }
  }

  public CurrentStatementHolder statement(@NonNull Statement statement) {
    synchronized (mutex) {
      this.statement = statement;
      this.wasCanceled = false;
      return this;
    }
  }

  public void clear() {
    synchronized (mutex) {
      statement = null;
      wasCanceled = false;
    }
  }

  public boolean wasCanceled() {
    synchronized (mutex) {
      return this.wasCanceled;
    }
  }
}
