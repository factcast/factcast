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
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.sql.*;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Timer;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import lombok.SneakyThrows;
import nl.altindag.log.LogCaptor;
import org.factcast.factus.projection.*;
import org.factcast.factus.serializer.*;
import org.factcast.factus.snapshot.SnapshotData;
import org.factcast.factus.snapshot.SnapshotIdentifier;
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

  @Mock Timer timer;

  @Nested
  class WhenCreatingTimer {
    @Test
    void createTimer() throws SQLException {
      when(dataSource.getConnection()).thenReturn(connection);
      when(connection.getMetaData().getTables(any(), any(), any(), any())).thenReturn(resultSet);
      when(resultSet.next()).thenReturn(true);

      ResultSet columns = mock(ResultSet.class);
      when(connection.getMetaData().getColumns(any(), any(), any(), any())).thenReturn(columns);
      when(columns.next()).thenReturn(true, true, true, true, true, true, false);
      when(columns.getString("COLUMN_NAME"))
          .thenReturn(
              "projection_class",
              "aggregate_id",
              "last_fact_id",
              "bytes",
              "snapshot_serializer_id",
              "last_accessed");

      JdbcSnapshotCache uut = new JdbcSnapshotCache(new JdbcSnapshotProperties(), dataSource);
      Timer timer = uut.createTimer();
      assertThat(timer).isNotNull();
    }
  }

  @Nested
  class WhenInstantiating {
    @Test
    void test_invalidNameForTable() {
      JdbcSnapshotProperties properties =
          new JdbcSnapshotProperties().setSnapshotTableName("name; drop table");
      assertThatThrownBy(() -> new JdbcSnapshotCache(properties, dataSource))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Invalid table name");
    }

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
          .hasMessageContaining("projection_class")
          .hasMessageContaining("aggregate_id")
          .hasMessageContaining("last_fact_id")
          .hasMessageContaining("bytes")
          .hasMessageContaining("snapshot_serializer_id")
          .hasMessageContaining("last_accessed");
    }

    @Test
    void test_doNotCreateAndTableIsNotValid_oneColumnMissing() throws SQLException {
      when(dataSource.getConnection()).thenReturn(connection);
      when(connection.getMetaData().getTables(any(), any(), any(), any())).thenReturn(resultSet);
      when(resultSet.next()).thenReturn(true);

      ResultSet columns = mock(ResultSet.class);
      when(connection.getMetaData().getColumns(any(), any(), any(), any())).thenReturn(columns);
      when(columns.next()).thenReturn(true, true, true, true, true, false);
      when(columns.getString("COLUMN_NAME"))
          .thenReturn(
              "projection_class",
              "aggregate_id",
              "last_fact_id",
              "bytes",
              "snapshot_serializer_id");

      JdbcSnapshotProperties properties = new JdbcSnapshotProperties();
      assertThatThrownBy(() -> new JdbcSnapshotCache(properties, dataSource))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining(
              "Snapshot table schema is not compatible with Factus. Missing columns: ")
          .hasMessageContaining("last_accessed");
    }

    @Test
    void test_tableIsValid() throws Exception {
      when(dataSource.getConnection()).thenReturn(connection);
      when(connection.getMetaData().getTables(any(), any(), any(), any())).thenReturn(resultSet);
      when(resultSet.next()).thenReturn(true);

      ResultSet columns = mock(ResultSet.class);
      when(connection.getMetaData().getColumns(any(), any(), any(), any())).thenReturn(columns);
      when(columns.next()).thenReturn(true, true, true, true, true, true, false);
      when(columns.getString("COLUMN_NAME"))
          .thenReturn(
              "projection_class",
              "aggregate_id",
              "last_fact_id",
              "bytes",
              "snapshot_serializer_id",
              "last_accessed");

      LogCaptor logCaptor = LogCaptor.forClass(JdbcSnapshotCache.class);

      assertDoesNotThrow(
          () ->
              new JdbcSnapshotCache(new JdbcSnapshotProperties(), dataSource) {
                @Override
                protected Timer createTimer() {
                  return timer;
                }
              });
      assertThat(logCaptor.getErrorLogs()).isEmpty();
      // make sure cleanup was scheduled
      verify(timer)
          .scheduleAtFixedRate(
              argThat(StaleSnapshotsTimerTask.class::isInstance),
              eq(0L),
              eq(TimeUnit.DAYS.toMillis(1)));
    }

    @Test
    void test_noCleanup() throws Exception {
      when(dataSource.getConnection()).thenReturn(connection);
      when(connection.getMetaData().getTables(any(), any(), any(), any())).thenReturn(resultSet);
      when(resultSet.next()).thenReturn(true);

      ResultSet columns = mock(ResultSet.class);
      when(connection.getMetaData().getColumns(any(), any(), any(), any())).thenReturn(columns);
      when(columns.next()).thenReturn(true, true, true, true, true, true, false);
      when(columns.getString("COLUMN_NAME"))
          .thenReturn(
              "projection_class",
              "aggregate_id",
              "last_fact_id",
              "bytes",
              "snapshot_serializer_id",
              "last_accessed");

      LogCaptor logCaptor = LogCaptor.forClass(JdbcSnapshotCache.class);

      JdbcSnapshotProperties properties = new JdbcSnapshotProperties();
      properties.setDeleteSnapshotStaleForDays(0);
      assertDoesNotThrow(
          () ->
              new JdbcSnapshotCache(properties, dataSource) {
                @Override
                protected Timer createTimer() {
                  return timer;
                }
              });
      assertThat(logCaptor.getErrorLogs()).isEmpty();
      // make sure cleanup was scheduled
      verifyNoInteractions(timer);
    }

    @Test
    void test_cleanupScheduled() throws Exception {
      when(dataSource.getConnection()).thenReturn(connection);
      when(connection.getMetaData().getTables(any(), any(), any(), any())).thenReturn(resultSet);
      when(resultSet.next()).thenReturn(true);

      ResultSet columns = mock(ResultSet.class);
      when(connection.getMetaData().getColumns(any(), any(), any(), any())).thenReturn(columns);
      when(columns.next()).thenReturn(true, true, true, true, true, true, false);
      when(columns.getString("COLUMN_NAME"))
          .thenReturn(
              "projection_class",
              "aggregate_id",
              "last_fact_id",
              "bytes",
              "snapshot_serializer_id",
              "last_accessed");

      LogCaptor logCaptor = LogCaptor.forClass(JdbcSnapshotCache.class);

      JdbcSnapshotProperties properties = new JdbcSnapshotProperties();
      properties.setDeleteSnapshotStaleForDays(2);
      assertDoesNotThrow(
          () ->
              new JdbcSnapshotCache(properties, dataSource) {
                @Override
                protected Timer createTimer() {
                  return timer;
                }
              });
      assertThat(logCaptor.getErrorLogs()).isEmpty();
      // make sure cleanup was scheduled
      verify(timer).scheduleAtFixedRate(any(), eq(0L), eq(TimeUnit.DAYS.toMillis(1)));
    }
  }

  @Nested
  class WhenCrud {
    @Mock PreparedStatement preparedStatement;
    private JdbcSnapshotCache jdbcSnapshotCache;

    @ProjectionMetaData(revision = 1L)
    class TestSnapshotProjection implements SnapshotProjection {}

    @ProjectionMetaData(revision = 1L)
    class TestAggregateProjection extends Aggregate {}

    @BeforeEach
    @SneakyThrows
    void setUp() {
      when(dataSource.getConnection()).thenReturn(connection);
      when(connection.getMetaData().getTables(any(), any(), any(), any())).thenReturn(resultSet);
      when(resultSet.next()).thenReturn(true);

      ResultSet columns = mock(ResultSet.class);
      when(connection.getMetaData().getColumns(any(), any(), any(), any())).thenReturn(columns);
      when(columns.next()).thenReturn(true, true, true, true, true, true, false);
      when(columns.getString("COLUMN_NAME"))
          .thenReturn(
              "projection_class",
              "aggregate_id",
              "last_fact_id",
              "bytes",
              "snapshot_serializer_id",
              "last_accessed");
      jdbcSnapshotCache =
          new JdbcSnapshotCache(
              new JdbcSnapshotProperties().setDeleteSnapshotStaleForDays(0), dataSource);
    }

    @Test
    @SneakyThrows
    void setSnapshotAgnosticallyViaUpdate() {
      final PreparedStatement update = mock(PreparedStatement.class);

      when(dataSource.getConnection()).thenReturn(connection);
      when(connection.prepareStatement(any())).thenReturn(update);

      SnapshotData snap =
          new SnapshotData(
              new byte[] {1, 2, 3}, SnapshotSerializerId.of("random"), UUID.randomUUID());

      when(update.executeUpdate()).thenReturn(1);
      jdbcSnapshotCache.store(SnapshotIdentifier.of(TestSnapshotProjection.class), snap);

      ArgumentCaptor<String> string = ArgumentCaptor.forClass(String.class);
      ArgumentCaptor<byte[]> bytes = ArgumentCaptor.forClass(byte[].class);
      ArgumentCaptor<Timestamp> timestamp = ArgumentCaptor.forClass(Timestamp.class);

      verify(update).executeUpdate();
      verify(update, times(4)).setString(any(Integer.class), string.capture());
      assertThat(string.getAllValues())
          .containsExactly(
              snap.lastFactId().toString(),
              "random",
              ScopedName.fromProjectionMetaData(TestSnapshotProjection.class).asString(),
              null);

      verify(update, times(1)).setTimestamp(any(Integer.class), timestamp.capture());
      assertThat(timestamp.getValue()).isEqualTo(Timestamp.valueOf(LocalDate.now().atStartOfDay()));

      verify(update, times(1)).setBytes(any(Integer.class), bytes.capture());
      assertThat(bytes.getValue()).isEqualTo(snap.serializedProjection());
    }

    @Test
    @SneakyThrows
    void setSnapshotAgnosticallyViaInsert() {
      final PreparedStatement update = mock(PreparedStatement.class);
      final PreparedStatement insert = mock(PreparedStatement.class);

      when(dataSource.getConnection()).thenReturn(connection);
      when(connection.prepareStatement(any())).thenReturn(update, insert);

      SnapshotData snap =
          new SnapshotData(
              new byte[] {1, 2, 3}, SnapshotSerializerId.of("random"), UUID.randomUUID());

      when(update.executeUpdate()).thenReturn(0); // update fails
      when(insert.executeUpdate()).thenReturn(1); // continuous with insert
      jdbcSnapshotCache.store(SnapshotIdentifier.of(TestSnapshotProjection.class), snap);

      // verify update statement
      verify(update).executeUpdate();
      ArgumentCaptor<String> stringsInUpdate = ArgumentCaptor.forClass(String.class);
      ArgumentCaptor<byte[]> bytesInUpdate = ArgumentCaptor.forClass(byte[].class);
      ArgumentCaptor<Timestamp> timestampsInUpdate = ArgumentCaptor.forClass(Timestamp.class);

      verify(update, times(4)).setString(any(Integer.class), stringsInUpdate.capture());
      assertThat(stringsInUpdate.getAllValues())
          .containsExactly(
              snap.lastFactId().toString(),
              "random",
              ScopedName.fromProjectionMetaData(TestSnapshotProjection.class).asString(),
              null);

      verify(update, times(1)).setTimestamp(any(Integer.class), timestampsInUpdate.capture());
      assertThat(timestampsInUpdate.getValue())
          .isEqualTo(Timestamp.valueOf(LocalDate.now().atStartOfDay()));

      verify(update, times(1)).setBytes(any(Integer.class), bytesInUpdate.capture());
      assertThat(bytesInUpdate.getValue()).isEqualTo(snap.serializedProjection());

      // verify insert statement
      verify(insert).executeUpdate();
      ArgumentCaptor<String> stringsInInsert = ArgumentCaptor.forClass(String.class);
      ArgumentCaptor<byte[]> bytesInInsert = ArgumentCaptor.forClass(byte[].class);
      ArgumentCaptor<Timestamp> timestampsInInsert = ArgumentCaptor.forClass(Timestamp.class);

      verify(insert, times(4)).setString(any(Integer.class), stringsInInsert.capture());
      assertThat(stringsInInsert.getAllValues())
          .containsExactly(
              ScopedName.fromProjectionMetaData(TestSnapshotProjection.class).asString(),
              null,
              snap.lastFactId().toString(),
              "random");

      verify(insert, times(1)).setTimestamp(any(Integer.class), timestampsInInsert.capture());
      assertThat(timestampsInInsert.getValue())
          .isEqualTo(Timestamp.valueOf(LocalDate.now().atStartOfDay()));

      verify(insert, times(1)).setBytes(any(Integer.class), bytesInInsert.capture());
      assertThat(bytesInInsert.getValue()).isEqualTo(snap.serializedProjection());
    }

    @Test
    @SneakyThrows
    void setSnapshotFails() {
      final PreparedStatement update = mock(PreparedStatement.class);
      final PreparedStatement insert = mock(PreparedStatement.class);

      when(dataSource.getConnection()).thenReturn(connection);
      when(connection.prepareStatement(any())).thenReturn(update, insert);

      SnapshotData snap =
          new SnapshotData(
              new byte[] {1, 2, 3}, SnapshotSerializerId.of("random"), UUID.randomUUID());

      when(update.executeUpdate()).thenReturn(0); // update fails
      when(insert.executeUpdate()).thenReturn(0); // and insert fails

      SnapshotIdentifier id = SnapshotIdentifier.of(TestSnapshotProjection.class);
      assertThatThrownBy(() -> jdbcSnapshotCache.store(id, snap))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Failed to insert snapshot into database. SnapshotId: ");
    }

    @Test
    @SneakyThrows
    void setSnapshot_fails() {
      when(dataSource.getConnection()).thenReturn(connection);
      when(connection.prepareStatement(any())).thenReturn(preparedStatement);

      SnapshotData snap =
          new SnapshotData(
              new byte[] {1, 2, 3}, SnapshotSerializerId.of("random"), UUID.randomUUID());

      when(preparedStatement.executeUpdate()).thenReturn(0);

      SnapshotIdentifier id = SnapshotIdentifier.of(TestSnapshotProjection.class);
      assertThatThrownBy(() -> jdbcSnapshotCache.store(id, snap))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Failed to insert snapshot into database. SnapshotId: ");
    }

    @Test
    @SneakyThrows
    void clearSnapshot() {
      when(dataSource.getConnection()).thenReturn(connection);
      when(connection.prepareStatement(any())).thenReturn(preparedStatement);

      jdbcSnapshotCache.remove(SnapshotIdentifier.of(TestSnapshotProjection.class));

      ArgumentCaptor<String> string = ArgumentCaptor.forClass(String.class);

      verify(preparedStatement, times(2)).setString(any(Integer.class), string.capture());
      verify(preparedStatement, times(1)).executeUpdate();

      assertThat(string.getAllValues())
          .containsExactly(
              ScopedName.fromProjectionMetaData(TestSnapshotProjection.class).asString(), null);
    }

    @Test
    @SneakyThrows
    void getSnapshot() {
      when(dataSource.getConnection()).thenReturn(connection);
      when(connection.prepareStatement(any())).thenReturn(preparedStatement);
      when(preparedStatement.executeQuery()).thenReturn(resultSet);
      when(resultSet.next()).thenReturn(true);

      UUID lastFactId = UUID.randomUUID();
      byte[] bytes = {1, 2, 3};

      when(resultSet.getBytes(1)).thenReturn(bytes);
      when(resultSet.getString(2)).thenReturn("serializerId");
      when(resultSet.getString(3)).thenReturn(lastFactId.toString());

      JdbcSnapshotCache uut = spy(jdbcSnapshotCache);
      doNothing().when(uut).updateLastAccessedTime(any());
      SnapshotIdentifier id = SnapshotIdentifier.of(TestSnapshotProjection.class);
      SnapshotData snapshot = uut.find(id).get();

      assertThat(snapshot.lastFactId()).isEqualTo(lastFactId);
      assertThat(snapshot.snapshotSerializerId().name()).isEqualTo("serializerId");
      assertThat(snapshot.serializedProjection()).isEqualTo(bytes);

      ArgumentCaptor<String> string = ArgumentCaptor.forClass(String.class);

      verify(preparedStatement, times(2)).setString(any(Integer.class), string.capture());
      verify(preparedStatement, times(1)).executeQuery();
      verify(uut, times(1)).updateLastAccessedTime(id);

      assertThat(string.getAllValues())
          .containsExactly(
              ScopedName.fromProjectionMetaData(TestSnapshotProjection.class).asString(), null);
    }

    @Test
    @SneakyThrows
    void getSnapshot_notFound() {
      when(dataSource.getConnection()).thenReturn(connection);
      when(connection.prepareStatement(any())).thenReturn(preparedStatement);
      when(preparedStatement.executeQuery()).thenReturn(resultSet);
      when(resultSet.next()).thenReturn(false);

      UUID uuid = UUID.randomUUID();
      Optional<SnapshotData> snapshot =
          jdbcSnapshotCache.find(SnapshotIdentifier.of(TestAggregateProjection.class, uuid));

      assertThat(snapshot).isEmpty();

      ArgumentCaptor<String> string = ArgumentCaptor.forClass(String.class);

      verify(preparedStatement, times(2)).setString(any(Integer.class), string.capture());
      verify(preparedStatement, times(1)).executeQuery();

      assertThat(string.getAllValues())
          .containsExactly(
              ScopedName.fromProjectionMetaData(TestAggregateProjection.class).asString(),
              uuid.toString());
    }

    @Test
    void testCreateKeyFor() {
      UUID uuid = UUID.fromString("a1d642dd-3ecd-4b58-ba24-deb8436cc329");
      assertThat(jdbcSnapshotCache.createKeyFor(SnapshotIdentifier.of(MyAgg.class, uuid)))
          .isEqualTo("hugo_1");

      assertThat(
              jdbcSnapshotCache.createKeyFor(
                  SnapshotIdentifier.of(TestAggregateProjection.class, uuid)))
          .isEqualTo(
              "org.factcast.core.snap.jdbc.JdbcSnapshotCacheTest$WhenCrud$TestAggregateProjection_1");

      assertThat(
              jdbcSnapshotCache.createKeyFor(SnapshotIdentifier.of(TestSnapshotProjection.class)))
          .isEqualTo(
              "org.factcast.core.snap.jdbc.JdbcSnapshotCacheTest$WhenCrud$TestSnapshotProjection_1");
    }
  }

  @ProjectionMetaData(name = "hugo", revision = 1)
  static class MyAgg extends Aggregate {}
}
