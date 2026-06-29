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

import java.sql.ResultSet;
import java.sql.SQLException;
import lombok.NonNull;
import lombok.experimental.Delegate;

public class ThrowingResultSet implements ResultSet {
  private final SQLException e;
  @Delegate ResultSet rs; // will throw NPE, if we're calling anything other than next()

  public ThrowingResultSet(@NonNull Exception e) {
    this.e = asSqlException(e);
  }

  private SQLException asSqlException(Exception e) {
    if (e instanceof SQLException sql) return sql;
    if (e.getCause() instanceof SQLException sql) return sql;
    // ok, not superclean, but hey...
    return new SQLException(e);
  }

  @Override
  public boolean next() throws SQLException {
    throw e;
  }
}
