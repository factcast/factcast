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
import lombok.NonNull;

public class DefaultFetchingQuery implements FetchingQuery {

  @Override
  public int executeAndProcess(
      @NonNull PreparedStatement ps,
      @NonNull RowProcessor rowProcessor,
      @NonNull CallbackAfterQueryFinished callbackBeforeProcessing)
      throws SQLException {
    int rows = 0;
    try (ps;
        ResultSet rs = ps.executeQuery()) {
      callbackBeforeProcessing.afterQueryFinished();
      while (rs.next() && !ps.isClosed()) {
        rowProcessor.process(rs);
        rows++;
      }
    }
    return rows;
  }
}
