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

import static org.mockito.Mockito.*;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultFetchingQueryTest {

  @Mock private PreparedStatement ps;
  @Mock private ResultSet resultSet;
  @Mock private FetchingQuery.RowProcessor rowProcessor;
  @Mock private FetchingQuery.CallbackAfterQueryFinished callback;

  private DefaultFetchingQuery underTest;

  @BeforeEach
  void setup() {
    underTest = new DefaultFetchingQuery();
  }

  @Test
  void testSuccessfulProcessing() throws SQLException {
    when(ps.executeQuery()).thenReturn(resultSet);
    when(resultSet.next()).thenReturn(true, true, false);

    underTest.executeAndProcess(ps, rowProcessor, callback);

    verify(callback).afterQueryFinished();
    verify(rowProcessor, times(2)).process(resultSet);
    verify(resultSet).close();
  }

  @Test
  void testExceptionInResultSetClosing() throws SQLException {
    when(ps.executeQuery()).thenReturn(resultSet);

    // DefaultFetchingQuery uses try-with-resources, so if ResultSet.close() throws,
    // it will be propagated.
    doThrow(new SQLException("close error")).when(resultSet).close();

    // If executeQuery succeeds, callback runs, next() returns false, then close() is called.
    // Wait, executeAndProcess does: try (ResultSet rs = ps.executeQuery()) { callback.run();
    // while(rs.next()) ... }
    // If next() returns false, it will still call close()

    when(resultSet.next()).thenReturn(false);

    try {
      underTest.executeAndProcess(ps, rowProcessor, callback);
    } catch (SQLException e) {
      // expected
    }

    verify(callback).afterQueryFinished();
    verify(resultSet).close();
  }
}
