/*
 * Copyright © 2017-2026 factcast.org
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
package org.factcast.store.internal.listen;

import com.google.common.annotations.VisibleForTesting;
import java.sql.*;
import java.util.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.jdbc.pool.PooledConnection;
import org.postgresql.jdbc.PgConnection;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

@Slf4j
public class ModifiedSingleConnectionDataSource extends SingleConnectionDataSource {
  private final Connection connection;
  @VisibleForTesting @Getter private final List<ConnectionModifier> modifiers;

  public ModifiedSingleConnectionDataSource(
      @NonNull Connection connection, @NonNull List<ConnectionModifier> modifiers) {
    super(connection, true);
    this.connection = connection;
    this.modifiers = List.copyOf(modifiers);

    this.modifiers.forEach(modifier -> modifier.afterBorrow(connection));
  }

  @Override
  public void destroy() {
    // we first need to check, if there is a still running transaction to roll back
    try {

      // no matter what state we thin, we're in: lets try to cancel any currently running statement
      tryCancel(connection);

      if (!connection.getAutoCommit()) {
        // if a TX is still running, that means that the catchup did not terminate successfully
        // (otherwise it needs to have committed).
        //
        log.debug("destroying the datasource while there still a running tx, rolling back.");
        tryRollback(connection);

        // TODO Dear reviewer:
        //
        // after some time playing with the catchup code, i am wondering, if
        // aborting/discarding is still necessary.
        // The code pushing signals to the pipeline is single-threaded, so that we get here either
        // by happy path, or because a RuntimeException was tripped.
        //
        // Either way, cancelling the running query and rolling back the TX should be enough,
        // because the wont be any code using the connection afterwards.
        //
        // Lets discuss.

        // discarding makes the pool drop the connection on reception, which is necessary as after
        // an abort it cannot be used any more and there is no way to recycle it.
        tryDiscard(connection);

        // by aborting the connection, we make sure that any attempt to further use
        // it in another
        // thread leads to "already closed"-Exceptions.
        // this is why we can omit frequent checking if something has been cancelled.
        tryAbort(connection);
      }
    } catch (SQLException e) {
      log.warn("rollback of dangling transaction failed on datasource destruction ", e);
    }

    // and make the pool drop it on reception

    var reversed = new ArrayList<>(modifiers);
    Collections.reverse(reversed);
    reversed.forEach(modifier -> modifier.beforeReturn(connection));
    super.destroy();
  }

  @VisibleForTesting
  void tryAbort(Connection connection) {
    try {
      PgConnection pgNative = connection.unwrap(PgConnection.class);
      if (pgNative == null)
        log.warn("Unwrapping of PgConnection failed. This is ok, if we're in a unit test");
      else pgNative.abort(Runnable::run);
    } catch (SQLException e) {
      log.warn("Discarding of connection failed on datasource destruction ", e);
    }
  }

  @VisibleForTesting
  void tryDiscard(Connection connection) {
    try {
      PooledConnection pooled = connection.unwrap(PooledConnection.class);
      if (pooled == null)
        log.warn("Unwrapping of PooledConnection failed. This is ok, if we're in a unit test");
      else pooled.setDiscarded(true);
    } catch (SQLException e) {
      log.warn("Discarding of connection failed on datasource destruction ", e);
    }
  }

  @VisibleForTesting
  void tryRollback(Connection connection) {
    try {
      this.connection.rollback();
    } catch (SQLException e) {
      log.warn("Rollback of dangling transaction failed on datasource destruction ", e);
    }
  }

  @VisibleForTesting
  void tryCancel(Connection connection) {
    // please note that if there is no query running, this is a no-op.
    try {
      PgConnection pgNative = connection.unwrap(PgConnection.class);
      if (pgNative == null)
        log.warn("Unwrapping of PgConnection failed. This is ok, if we're in a unit test");
      else pgNative.cancelQuery();
    } catch (SQLException e) {
      log.warn("Cancelling of orphaned query failed on datasource destruction ", e);
    }
  }
}
