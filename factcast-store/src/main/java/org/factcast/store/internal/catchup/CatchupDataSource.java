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
package org.factcast.store.internal.catchup;

import com.google.common.annotations.VisibleForTesting;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.jdbc.pool.PooledConnection;
import org.factcast.store.internal.*;
import org.factcast.store.internal.pipeline.PushbackServerPipeline;
import org.postgresql.jdbc.PgConnection;

/**
 * This must only be used for catching up. it is special in a sense that it makes sure to understand
 * when a catchup is prematurely canceled, so that it can cancel a query if there is one running.
 *
 * <p>We expect this to be used in a try-with-resource fashion, so that the close is guaranteed to
 * happen also in exceptional situations.
 *
 * <p>This is necessary when for instance a PgChunkedWithHoldCursorCatchup is doing its initial
 * query (that may take some time) and the cancel reason somes from the client communication. Here
 * it is not enough to close the Pipeline, because it might make quite some time before the first
 * fact will be added to the pipeline, just to understand that the pipeline is closed and the
 * process is terminated.
 *
 * <p>If the catchup is in between queries when the cancel is called, we abort the connection so
 * that every subsequent interaction with it will throw a runtime exception.
 *
 * <p>Also, iteracting with the pipeline after it is close with raise an exception. This way we
 * should be sure enough that not too much unnecessary work is done in the Catchup implementations
 * after cancel has happend.
 */
@Slf4j
public class CatchupDataSource extends ModifiedSingleConnectionDataSource {
  private Connection connection;
  private final PushbackServerPipeline pipeline;
  private final AtomicBoolean wasCanceled = new AtomicBoolean(false);

  public CatchupDataSource(
      @NonNull Connection connection,
      @NonNull List<ConnectionModifier> modifiers,
      @NonNull PushbackServerPipeline pipeline) {
    super(connection, modifiers);
    this.connection = connection;
    this.pipeline = pipeline;

    // we want to know when the pipeline is closed, so that we can cancel and abort.
    pipeline.register(this);
  }

  @Override
  public void destroy() {
    // lets deregister from the pipeline, first
    pipeline.unregister(this);

    if (wasCanceled.get()) {
      // ok, we received a cancel call before from another thread, so we're not in the happy
      // try-with-resource path. For safety reasons, we killed the connection already completely.
      //
      // nothing to do here.
    } else {

      // we prepare this connection to get reused.
      try {
        // we might have gotten here because of an Exception being thrown by the catchup thread
        // itself.
        // this should not really have a statement in a running state, but we try to cancel it
        // anyway.
        //
        // If there is no statement running, this is a no-op.
        tryCancel(connection);

        if (!connection.getAutoCommit())
          // we can rollback, as catchup is read only, anyway.
          tryRollback(connection);

      } catch (SQLException meh) {
        log.warn("Error preparing a connection for reuse", meh);
        // if anything exceptional happened here, we decide the connection is not safe to return, so
        // we will kill it nevertheless. Better safe than sorry.
        tryAbort(connection);
        tryDiscard(connection);
      }
    }

    // just to be sure not keeping dangling references
    connection = null;

    // this will return the connection to the pool, which decides if to discard or reuse it, based
    // on setDiscard
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
      log.warn("Aborting of connection failed on datasource destruction ", e);
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

  /** This may be called from another thread. */
  public void cancel() {
    this.wasCanceled.set(true);
    log.debug("Cancellation requested");

    // this should throw a SQLException on the thread waiting for the statement to return.
    // if however, it currently isn't waiting for a query, we kill the connection to
    // produce runtime exceptions on that Thread asap.
    tryCancel(connection);
    tryAbort(connection);
    tryDiscard(connection);
  }
}
