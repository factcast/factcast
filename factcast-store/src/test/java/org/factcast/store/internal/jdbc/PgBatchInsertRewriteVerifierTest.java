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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.sql.Connection;
import javax.sql.DataSource;
import nl.altindag.log.LogCaptor;
import org.junit.jupiter.api.Test;
import org.postgresql.core.QueryExecutor;
import org.postgresql.jdbc.PgConnection;

class PgBatchInsertRewriteVerifierTest {

  @Test
  void acceptsEnabledRewrite() throws Exception {
    DataSource dataSource = mock(DataSource.class);
    Connection connection = mock(Connection.class);
    PgConnection pgConnection = mock(PgConnection.class);
    QueryExecutor queryExecutor = mock(QueryExecutor.class);
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.unwrap(PgConnection.class)).thenReturn(pgConnection);
    when(pgConnection.getQueryExecutor()).thenReturn(queryExecutor);
    when(queryExecutor.isReWriteBatchedInsertsEnabled()).thenReturn(true);

    assertThatCode(() -> new PgBatchInsertRewriteVerifier(dataSource)).doesNotThrowAnyException();

    verify(connection).close();
  }

  @Test
  void rejectsDisabledRewrite() throws Exception {

    LogCaptor logCaptor = LogCaptor.forClass(PgBatchInsertRewriteVerifier.class);

    DataSource dataSource = mock(DataSource.class);
    Connection connection = mock(Connection.class);
    PgConnection pgConnection = mock(PgConnection.class);
    QueryExecutor queryExecutor = mock(QueryExecutor.class);
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.unwrap(PgConnection.class)).thenReturn(pgConnection);
    when(pgConnection.getQueryExecutor()).thenReturn(queryExecutor);
    new PgBatchInsertRewriteVerifier(dataSource);
    assertThat(logCaptor.getWarnLogs())
        .hasSize(1)
        .allMatch(s -> s.contains("reWriteBatchedInserts=true"));

    verify(connection).close();
  }
}
