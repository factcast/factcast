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
import com.google.common.base.Preconditions;
import java.sql.*;
import java.util.concurrent.atomic.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

/**
 * Holder for a current statement. This is used to cancel the statement in case of a timeout or to
 * know if it was canceled from another thread. Note that this is not a singleton, but scoped to a
 * PGFactStream execution.
 */
@Slf4j
public class CurrentStatementHolder {
  private final AtomicReference<Statement> statement = new AtomicReference<>();
  private final AtomicBoolean wasCanceled = new AtomicBoolean(false);
  private final AtomicBoolean wasDestroyed = new AtomicBoolean(false);

  public boolean wasCanceled() {
    return this.wasCanceled.get();
  }

  public boolean wasDestroyed() {
    return this.wasDestroyed.get();
  }

  public boolean hasStatement() {
    return statement.get() != null;
  }

  /** wraps given connection into one that registers statements to the holder. */
  public @NonNull Connection track(@NonNull Connection connection) {
    checkState();
    return new StatementTrackingConnection(connection, this);
  }

  // this is called destroy rather than close to make obvious that it can no longer be used at all
  public void destroy() {
    if (!wasDestroyed()) {
      // if we still have a statement, we need to cancel it.
      // note that cancelling a statement involves closing the connection, which is fine, as it
      // happens only at the end of a PGStream usage.
      Statement st = statement.get();
      if (st != null) {
        log.warn("cancelling statement on destroy {}. This is a bug.", st);
        cancel();
      }
    }
    // otherwise, there is nothing to do. Actually, it would be the expected state and behavior.
    wasDestroyed.set(true);
  }

  public void cancel() {
    checkState();

    Statement st = statement.get();
    if (st != null) {
      // not elegant, but plenty of different things can go wrong
      try {
        cancelStatement(st);
        // before, we rolled the tx back, if the associated connection had one.
        // As Transaction management is non of our business, and this is used for catching up only
        // (so that transactions do not have any changes), we stop messing with what is out of our
        // scope here. The connection will be recycled before returning it to the pool.
      } finally {
        unregister(st);
        wasCanceled.set(true);
      }
    } else {
      log.trace("Statement not set, so no canceling necessary.");
    }
  }

  // ----------------- package private and testing from here on

  void register(@NonNull Statement s) {
    checkState();

    Statement oldStatement = statement.getAndSet(s);
    if (oldStatement != null) {
      log.warn("Registration of Statement canceling an older one. This is a bug.");
      try {
        oldStatement.cancel();
      } catch (SQLException ignore) {
        log.warn("While canceling orphaned statement:", ignore);
      }
    }
  }

  void unregister(Statement st) {
    checkState();

    Statement oldStatement = statement.getAndSet(null);
    if (oldStatement == null) log.warn("Unnecessary unregister of {}. This is a bug.", st);
    if (oldStatement != st)
      log.warn(
          "Statement confusion: We're unregistering a statement that is not currently registered. This is a bug.");
  }

  @VisibleForTesting
  void cancelStatement(@NonNull Statement st) {
    log.info("Canceling statement {}", st);
    try {
      st.cancel();
      st.close();
    } catch (SQLException e) {
      log.debug("Exception while cancelling statement {}:", statement, e);
    }
  }

  @VisibleForTesting
  Statement statement() {
    return statement.get();
  }

  private void checkState() {
    Preconditions.checkState(!wasDestroyed.get(), "already closed");
    Preconditions.checkState(!wasCanceled.get(), "already canceled");
  }
}
