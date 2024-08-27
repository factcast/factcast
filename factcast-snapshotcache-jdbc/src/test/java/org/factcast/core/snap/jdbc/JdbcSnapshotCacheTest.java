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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import lombok.SneakyThrows;
import nl.altindag.log.LogCaptor;
import org.factcast.core.snap.Snapshot;
import org.factcast.core.snap.SnapshotId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JdbcSnapshotCacheTest {
  @Mock DataSource dataSource;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  Connection connection;

  @Mock ResultSet resultSet;

  @Nested
  class WhenInstantiating {
    @Test
    void test_doNotCreateAndTableDoesntExist() throws SQLException {
      when(dataSource.getConnection()).thenReturn(connection);
      when(connection.getMetaData().getTables(any(), any(), any(), any())).thenReturn(resultSet);
      when(resultSet.next()).thenReturn(false);

      JdbcSnapshotProperties properties = new JdbcSnapshotProperties();
      assertThatThrownBy(() -> new JdbcSnapshotCache(properties, dataSource))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Snapshots table does not exist: ");
    }

    @Test
    void test_doNotCreateAndTableIsNotValid() throws SQLException {
      when(dataSource.getConnection()).thenReturn(connection);
      when(connection.getMetaData().getTables(any(), any(), any(), any())).thenReturn(resultSet);
      when(resultSet.next()).thenReturn(true);

      ResultSet columns = mock(ResultSet.class);
      when(connection.getMetaData().getColumns(any(), any(), any(), any())).thenReturn(columns);
      when(columns.next()).thenReturn(true, false);
      when(columns.getString("COLUMN_NAME")).thenReturn("another");

      JdbcSnapshotProperties properties = new JdbcSnapshotProperties();
      assertThatThrownBy(() -> new JdbcSnapshotCache(properties, dataSource))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining(
              "Snapshot table schema is not compatible with Factus. Missing columns: ")
          .hasMessageContaining("key")
          .hasMessageContaining("uuid")
          .hasMessageContaining("last_fact_id")
          .hasMessageContaining("bytes")
          .hasMessageContaining("compressed");
    }

    @Test
    void test_doNotCreateAndTableIsNotValid_oneColumnMissing() throws SQLException {
      when(dataSource.getConnection()).thenReturn(connection);
      when(connection.getMetaData().getTables(any(), any(), any(), any())).thenReturn(resultSet);
      when(resultSet.next()).thenReturn(true);

      ResultSet columns = mock(ResultSet.class);
      when(connection.getMetaData().getColumns(any(), any(), any(), any())).thenReturn(columns);
      when(columns.next()).thenReturn(true, true, true, true, false);
      when(columns.getString("COLUMN_NAME")).thenReturn("key", "uuid", "last_fact_id", "bytes");

      JdbcSnapshotProperties properties = new JdbcSnapshotProperties();
      assertThatThrownBy(() -> new JdbcSnapshotCache(properties, dataSource))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining(
              "Snapshot table schema is not compatible with Factus. Missing columns: ")
          .hasMessageContaining("compressed");
    }

    @Test
    void test_tableIsValid() throws Exception {
      when(dataSource.getConnection()).thenReturn(connection);
      when(connection.getMetaData().getTables(any(), any(), any(), any())).thenReturn(resultSet);
      when(resultSet.next()).thenReturn(true);

      ResultSet columns = mock(ResultSet.class);
      when(connection.getMetaData().getColumns(any(), any(), any(), any())).thenReturn(columns);
      when(columns.next()).thenReturn(true, true, true, true, true, false);
      when(columns.getString("COLUMN_NAME"))
          .thenReturn("key", "uuid", "last_fact_id", "bytes", "compressed");

      LogCaptor logCaptor = LogCaptor.forClass(JdbcSnapshotCache.class);
      assertDoesNotThrow(() -> new JdbcSnapshotCache(new JdbcSnapshotProperties(), dataSource));
      // Give time for the thread executor to run
      Thread.sleep(100);

      ArgumentCaptor<String> string = ArgumentCaptor.forClass(String.class);
      verify(connection).prepareStatement(string.capture());

      assertThat(logCaptor.getErrorLogs()).hasSize(0);
      assertThat(string.getValue()).startsWith("DELETE FROM ");
      assertThat(string.getValue()).contains("WHERE last_accessed >");
    }

    @Test
    void test_cleanupFails() throws Exception {
      when(dataSource.getConnection()).thenReturn(connection);
      when(connection.getMetaData().getTables(any(), any(), any(), any())).thenReturn(resultSet);
      when(resultSet.next()).thenReturn(true);

      ResultSet columns = mock(ResultSet.class);
      when(connection.getMetaData().getColumns(any(), any(), any(), any())).thenReturn(columns);
      when(columns.next()).thenReturn(true, true, true, true, true, false);
      when(columns.getString("COLUMN_NAME"))
          .thenReturn("key", "uuid", "last_fact_id", "bytes", "compressed");

      LogCaptor logCaptor = LogCaptor.forClass(JdbcSnapshotCache.class);
      doThrow(new SQLException("")).when(connection).prepareStatement(any());

      assertDoesNotThrow(() -> new JdbcSnapshotCache(new JdbcSnapshotProperties(), dataSource));
      // Give time for the thread executor to run
      Thread.sleep(100);

      ArgumentCaptor<String> string = ArgumentCaptor.forClass(String.class);
      verify(connection).prepareStatement(string.capture());

      assertThat(logCaptor.getErrorLogs()).hasSize(1);
      assertThat(logCaptor.getErrorLogs().get(0)).contains("Failed to delete old snapshots");
      assertThat(string.getValue()).startsWith("DELETE FROM ");
      assertThat(string.getValue()).contains("WHERE last_accessed >");
    }
  }

  @Nested
  class WhenCrud {
    @Mock PreparedStatement preparedStatement;
    private JdbcSnapshotCache jdbcSnapshotCache;

    @BeforeEach
    @SneakyThrows
    void setUp() {
      when(dataSource.getConnection()).thenReturn(connection);
      when(connection.getMetaData().getTables(any(), any(), any(), any())).thenReturn(resultSet);
      when(resultSet.next()).thenReturn(true);

      ResultSet columns = mock(ResultSet.class);
      when(connection.getMetaData().getColumns(any(), any(), any(), any())).thenReturn(columns);
      when(columns.next()).thenReturn(true, true, true, true, true, false);
      when(columns.getString("COLUMN_NAME"))
          .thenReturn("key", "uuid", "last_fact_id", "bytes", "compressed");
      jdbcSnapshotCache =
          new JdbcSnapshotCache(
              new JdbcSnapshotProperties().setDeleteSnapshotStaleForDays(0), dataSource);
    }

    @Test
    @SneakyThrows
    void setSnapshot() {
      when(dataSource.getConnection()).thenReturn(connection);
      when(connection.prepareStatement(any())).thenReturn(preparedStatement);

      Snapshot snap =
          new Snapshot(
              SnapshotId.of("", UUID.randomUUID()), UUID.randomUUID(), new byte[] {1, 2, 3}, false);

      when(preparedStatement.executeUpdate()).thenReturn(1);
      jdbcSnapshotCache.setSnapshot(snap);

      ArgumentCaptor<String> string = ArgumentCaptor.forClass(String.class);
      ArgumentCaptor<byte[]> bytes = ArgumentCaptor.forClass(byte[].class);
      ArgumentCaptor<Boolean> bool = ArgumentCaptor.forClass(Boolean.class);

      verify(preparedStatement, times(3)).setString(any(Integer.class), string.capture());
      assertThat(string.getAllValues())
          .containsExactly(
              snap.id().key(), snap.id().uuid().toString(), snap.lastFact().toString());

      verify(preparedStatement, times(1)).setBoolean(any(Integer.class), bool.capture());
      assertThat(bool.getValue()).isEqualTo(snap.compressed());

      verify(preparedStatement, times(1)).setBytes(any(Integer.class), bytes.capture());
      assertThat(bytes.getValue()).isEqualTo(snap.bytes());
    }

    @Test
    @SneakyThrows
    void setSnapshot_fails() {
      when(dataSource.getConnection()).thenReturn(connection);
      when(connection.prepareStatement(any())).thenReturn(preparedStatement);

      Snapshot snap =
          new Snapshot(
              SnapshotId.of("", UUID.randomUUID()), UUID.randomUUID(), new byte[] {1, 2, 3}, false);

      when(preparedStatement.executeUpdate()).thenReturn(0);
      assertThatThrownBy(() -> jdbcSnapshotCache.setSnapshot(snap))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Failed to insert snapshot into database. SnapshotId: ");
    }

    @Test
    @SneakyThrows
    void clearSnapshot() {
      when(dataSource.getConnection()).thenReturn(connection);
      when(connection.prepareStatement(any())).thenReturn(preparedStatement);

      SnapshotId id = SnapshotId.of("", UUID.randomUUID());

      jdbcSnapshotCache.clearSnapshot(id);

      ArgumentCaptor<String> string = ArgumentCaptor.forClass(String.class);

      verify(preparedStatement, times(2)).setString(any(Integer.class), string.capture());
      verify(preparedStatement, times(1)).executeUpdate();

      assertThat(string.getAllValues()).containsExactly(id.key(), id.uuid().toString());
    }

    @Test
    @SneakyThrows
    void getSnapshot() {
      when(dataSource.getConnection()).thenReturn(connection);
      when(connection.prepareStatement(any())).thenReturn(preparedStatement);
      when(preparedStatement.executeQuery()).thenReturn(resultSet);
      when(resultSet.next()).thenReturn(true);

      UUID aggregateId = UUID.randomUUID();
      UUID lastFactId = UUID.randomUUID();
      byte[] bytes = {1, 2, 3};

      when(resultSet.getString(1)).thenReturn("key");
      when(resultSet.getString(2)).thenReturn(aggregateId.toString());
      when(resultSet.getString(3)).thenReturn(lastFactId.toString());
      when(resultSet.getBytes(4)).thenReturn(bytes);
      when(resultSet.getBoolean(5)).thenReturn(false);

      SnapshotId id = SnapshotId.of("key", aggregateId);
      Snapshot snapshot = jdbcSnapshotCache.getSnapshot(id).get();

      assertThat(snapshot.id().key()).isEqualTo("key");
      assertThat(snapshot.id().uuid()).isEqualTo(aggregateId);
      assertThat(snapshot.lastFact()).isEqualTo(lastFactId);
      assertThat(snapshot.bytes()).isEqualTo(bytes);
      assertThat(snapshot.compressed()).isFalse();

      ArgumentCaptor<String> string = ArgumentCaptor.forClass(String.class);

      verify(preparedStatement, times(2)).setString(any(Integer.class), string.capture());
      verify(preparedStatement, times(1)).executeQuery();

      assertThat(string.getAllValues()).containsExactly(id.key(), id.uuid().toString());
    }

    @Test
    @SneakyThrows
    void getSnapshot_notFound() {
      when(dataSource.getConnection()).thenReturn(connection);
      when(connection.prepareStatement(any())).thenReturn(preparedStatement);
      when(preparedStatement.executeQuery()).thenReturn(resultSet);
      when(resultSet.next()).thenReturn(false);

      SnapshotId id = SnapshotId.of("key", UUID.randomUUID());
      Optional<Snapshot> snapshot = jdbcSnapshotCache.getSnapshot(id);

      assertThat(snapshot).isEmpty();

      ArgumentCaptor<String> string = ArgumentCaptor.forClass(String.class);

      verify(preparedStatement, times(2)).setString(any(Integer.class), string.capture());
      verify(preparedStatement, times(1)).executeQuery();

      assertThat(string.getAllValues()).containsExactly(id.key(), id.uuid().toString());
    }
  }
}
