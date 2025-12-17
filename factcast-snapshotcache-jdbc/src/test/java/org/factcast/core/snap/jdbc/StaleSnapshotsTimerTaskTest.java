/*
 * Copyright © 2017-2024 factcast.org
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
package org.factcast.core.snap.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import javax.sql.DataSource;
import lombok.NonNull;
import lombok.SneakyThrows;
import nl.altindag.log.LogCaptor;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StaleSnapshotsTimerTaskTest {

  private static final String TABLE_NAME = "TABLE_NAME";
  private static final String LAST_ACCESSED_TABLE_NAME = "LAST_ACCESSED_TABLE_NAME";
  @Mock private @NonNull DataSource dataSource;
  @Mock private @NonNull Connection conn;
  @Mock private @NonNull PreparedStatement statement;

  @Nested
  class WhenRunning {
    @SneakyThrows
    @BeforeEach
    void setup() {
      Mockito.when(dataSource.getConnection()).thenReturn(conn);
      Mockito.when(conn.prepareStatement(ArgumentMatchers.any())).thenReturn(statement);
    }

    @SneakyThrows
    @Test
    void logsException() {
      try (LogCaptor logCaptor = LogCaptor.forClass(StaleSnapshotsTimerTask.class)) {

        Mockito.when(statement.executeUpdate()).thenThrow(SQLException.class);
        new StaleSnapshotsTimerTask(dataSource, TABLE_NAME, LAST_ACCESSED_TABLE_NAME, 90).run();

        Assertions.assertThat(logCaptor.getErrorLogs())
            .isNotEmpty()
            .contains("Failed to delete old snapshots");
      }
    }

    @SneakyThrows
    @Test
    void happyPath() {

      new StaleSnapshotsTimerTask(dataSource, TABLE_NAME, LAST_ACCESSED_TABLE_NAME, 90).run();
      Timestamp expectedTimestamp = Timestamp.valueOf(LocalDate.now().atStartOfDay().minusDays(90));

      InOrder inOrder = Mockito.inOrder(statement);
      inOrder.verify(statement).setTimestamp(1, expectedTimestamp);
      inOrder.verify(statement).executeUpdate();
    }
  }
}
