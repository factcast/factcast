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
import org.factcast.store.internal.catchup.RowProcessor;

@SuppressWarnings("java:S2142")
public class PreFetchingQuery implements FetchingQuery {
  // to not pass nulls
  private static final @NonNull Runnable NOP = () -> {};

  @Override
  public void executeAndProcess(
      @NonNull PreparedStatement ps,
      @NonNull RowProcessor rowProcessor,
      @NonNull Runnable callbackBeforeProcessing)
      throws SQLException {

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

    ResultSet resultSet = ps.executeQuery();
    callbackBeforeProcessing.run();

    // async producer, terminated by closing the rs
    CompletableFuture.runAsync(() -> produce(resultSet, q));

    // sync consumer
    try {

      boolean exhausted;
      do {
        exhausted = true;
        ResultSet page = q.take();

        while (page.next()) {
          exhausted = false;
          rowProcessor.process(page);
        }

      } while (!exhausted);

    } catch (InterruptedException e) {
      // in that case we do not care.
    } finally {
      synchronized (resultSet) {
        if (!resultSet.isClosed()) resultSet.close();
      }
      q.clear();
    }
  }

  @SuppressWarnings({"java:S2445", "java:S2095", "SynchronizationOnLocalVariableOrMethodParameter"})
  private void produce(@NonNull ResultSet resultSet, @NonNull ArrayBlockingQueue<ResultSet> q) {
    try {
      while (!resultSet.isClosed()) {
        CachedRowSet crs = RowSetProvider.newFactory().createCachedRowSet();
        synchronized (resultSet) {
          crs.populate(new ResultSetPage(resultSet));
        }
        put(q, crs); // this will block if the queue is full
      }

      // signal the end
      put(q, RowSetProvider.newFactory().createCachedRowSet());
    } catch (Exception e) {
      // we're relying on the consumer to instanceOf
      put(q, new ThrowingResultSet(e));
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

  // convenience
  public void executeAndProcess(PreparedStatement ps, RowProcessor rowProcessor)
      throws SQLException {
    executeAndProcess(ps, rowProcessor, NOP);
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
