/*
 * Copyright © 2017-2020 factcast.org
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
package org.factcast.store.internal.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.core.QueryExecutor;
import org.postgresql.jdbc.PgConnection;

/** Ensures the writable store's JDBC batches are rewritten into multi-row INSERT statements. */
@Slf4j
public final class PgBatchInsertRewriteVerifier {

  public PgBatchInsertRewriteVerifier(DataSource dataSource) {
    try (Connection connection = dataSource.getConnection()) {
      PgConnection pgConnection = connection.unwrap(PgConnection.class);
      QueryExecutor queryExecutor = pgConnection.getQueryExecutor();
      if (!queryExecutor.isReWriteBatchedInsertsEnabled()) {
        log.warn(
            "FactStore works much better with reWriteBatchedInserts=true on the PostgreSQL datasource. Please consider adding it.");
      }
    } catch (SQLException e) {
      log.error("Could not verify reWriteBatchedInserts on the PostgreSQL datasource", e);
    }
  }
}
