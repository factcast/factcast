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
package org.factcast.store.internal.horizon;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PgFactStreamHorizonProviderTest {
  @ParameterizedTest
  @ValueSource(longs = {0, 42})
  void readsPrimaryBoundaryAndReleasesJdbcResources(long serial) throws Exception {
    var primary = mock(DataSource.class);
    var connection = mock(Connection.class);
    var statement = mock(Statement.class);
    var rows = mock(ResultSet.class);
    when(primary.getConnection()).thenReturn(connection);
    when(connection.createStatement()).thenReturn(statement);
    when(statement.executeQuery("SELECT COALESCE(MAX(ser),0) FROM fact")).thenReturn(rows);
    when(rows.next()).thenReturn(true, false);
    when(rows.getLong(1)).thenReturn(serial);

    assertThat(new PgFactStreamHorizonProvider(primary).get().factSerial()).isEqualTo(serial);

    verify(rows).close();
    verify(statement).close();
    verify(connection).close();
  }

  @Test
  void readsSuppliedDatasourceWithoutBorrowingFromPrimary() throws Exception {
    var primary = mock(DataSource.class);
    var replica = mock(DataSource.class);
    var connection = mock(Connection.class);
    var statement = mock(Statement.class);
    var rows = mock(ResultSet.class);
    when(replica.getConnection()).thenReturn(connection);
    when(connection.createStatement()).thenReturn(statement);
    when(statement.executeQuery("SELECT COALESCE(MAX(ser),0) FROM fact")).thenReturn(rows);
    when(rows.next()).thenReturn(true, false);
    when(rows.getLong(1)).thenReturn(17L);

    assertThat(new PgFactStreamHorizonProvider(primary).readFrom(replica).factSerial())
        .isEqualTo(17);

    verifyNoInteractions(primary);
    verify(connection).close();
  }
}
