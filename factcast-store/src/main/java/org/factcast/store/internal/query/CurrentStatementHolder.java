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
import java.sql.SQLException;
import java.sql.Statement;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CurrentStatementHolder implements Closeable {
  private final Object mutex = new Object();
  private Statement statement;
  private boolean wasCanceled;
  // for post-commit fetches in HOLD_CURSOR mode, rolling-back makes no sense
  private boolean rollbackOnCancel = true;

  @Override
  public void close() {
    synchronized (mutex) {
      if (statement != null) {
        log.info("Canceling statement " + statement);
        try {
          statement.cancel();

          if (rollbackOnCancel) {
            // we have to roll back the tx on the underlying connection
            // if we do not end the transaction, statements are cancelled but still "idle in
            // transaction" and so block further actions like wiping between tests
            statement.getConnection().rollback();
          }
        } catch (SQLException e) {
          log.debug("Exception while closing statement {}:", statement, e);
        } finally {
          wasCanceled = true;
        }
      }
    }
  }

  public CurrentStatementHolder statement(@NonNull Statement statement) {
    return statement(statement, true);
  }

  public CurrentStatementHolder statement(@NonNull Statement statement, boolean rollbackOnCancel) {
    synchronized (mutex) {
      this.statement = statement;
      this.wasCanceled = false;
      this.rollbackOnCancel = rollbackOnCancel;
      return this;
    }
  }

  public void clear() {
    synchronized (mutex) {
      statement = null;
      wasCanceled = false;
      rollbackOnCancel = true;
    }
  }

  public boolean wasCanceled() {
    synchronized (mutex) {
      return this.wasCanceled;
    }
  }
}
