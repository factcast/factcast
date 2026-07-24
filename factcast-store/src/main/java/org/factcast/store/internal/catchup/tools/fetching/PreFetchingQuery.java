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
package org.factcast.store.internal.catchup.tools.fetching;

import java.sql.*;
import java.util.concurrent.*;
import javax.sql.rowset.*;
import lombok.NonNull;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;

@SuppressWarnings("java:S2142")
@Slf4j
public class PreFetchingQuery implements FetchingQuery {

  @Override
  public int executeAndProcess(
      @NonNull PreparedStatement ps,
      @NonNull RowProcessor rowProcessor,
      @NonNull CallbackAfterQueryFinished callbackBeforeProcessing)
      throws SQLException {

    @SuppressWarnings("ReassignedVariable")
    int rows = 0;

    if (ps.getConnection().getAutoCommit())
      throw new IllegalArgumentException(
          "For PreFetchingQuery to work, we need to be in a transaction.");

    int fetchSize = ps.getFetchSize();
    if (fetchSize == 0)
      throw new IllegalArgumentException("Fetch size is not set on the PreparedStatement.");

    ArrayBlockingQueue<ResultSet> q =
        new ArrayBlockingQueue<>(1); // one in progress, one prefetched
    // set to half the fetch size, so that we end up using the
    // same kind of heap memory, if we have one RS in processing
    // and one waiting.
    ps.setFetchSize(fetchSize / 2);

    try (ps;
        ResultSet resultSet = ps.executeQuery()) {
      callbackBeforeProcessing.afterQueryFinished();

      // async producer, terminated by closing the rs
      CompletableFuture.runAsync(() -> produce(resultSet, q));

      // sync consumer
      try {

        int pageNumber = 0;
        boolean exhausted;
        do {
          exhausted = true;
          ResultSet page = q.take();

          log.trace("processing page {}", ++pageNumber);

          while (page.next() && !ps.isClosed()) {
            exhausted = false;
            rowProcessor.process(page);
            rows++;
          }
          page.close(); // not strictly necessary
        } while (!exhausted);

      } catch (InterruptedException e) {
        // in that case we do not care.
      }
      return rows;
    } finally {
      // as the statement is closed by now, this should unblock a producer, and give it the chance
      // to terminate
      q.clear();
    }
  }

  @SuppressWarnings({
    "java:S2445",
    "java:S2095",
    "java:S2093",
    "SynchronizationOnLocalVariableOrMethodParameter"
  })
  private void produce(@NonNull ResultSet resultSet, @NonNull ArrayBlockingQueue<ResultSet> q) {
    //noinspection TryFinallyCanBeTryWithResources
    try {
      int page = 0;
      while (!resultSet.isClosed()) {
        CachedRowSet crs = RowSetProvider.newFactory().createCachedRowSet();
        synchronized (resultSet) {
          log.trace("fetching page {} to memory (size={})", ++page, resultSet.getFetchSize());
          crs.populate(new ResultSetPage(resultSet));
        }
        put(q, crs); // this will block if the queue is full
      }

      // signal the end
      put(q, RowSetProvider.newFactory().createCachedRowSet());
    } catch (Exception e) {
      // we're relying on the consumer to instanceOf
      put(q, new ThrowingResultSet(e));
    } finally {
      try {
        resultSet.close();
      } catch (SQLException e) {
        log.debug("Chunk resultset cannot be closed: ", e);
      }
    }
  }

  /**
   * The only reason for this method to exist (apart from swallowing InterruptedException) is to
   * remind the reader that we're using Queue.put here instead of Queue.add, as add would throw an
   * exception on a full queue, while put blocks (which is exactly what we want here)
   */
  private void put(ArrayBlockingQueue<ResultSet> q, ResultSet rs) {
    try {
      q.put(rs);
    } catch (InterruptedException e) {
      // in that case we do not care.
    }
  }

  /**
   * Wraps a given ResultSet and limits the number of rows returned. Only intended for use with
   * CachedRowSet.
   */
  private static class ResultSetPage implements ResultSet {
    @Delegate final ResultSet rs;
    private final int limit;
    private int row = 0;

    public ResultSetPage(@NonNull ResultSet source) throws SQLException {
      rs = source;
      limit = source.getFetchSize();
    }

    public boolean next() throws SQLException {
      if (++row > limit) return false;
      else return rs.next();
    }
  }
}
