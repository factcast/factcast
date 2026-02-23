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
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;
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
  public static final String TABLE_NAME = "table_snapshots";
  public static final String LAST_ACCESSED_TABLE_NAME = "table_lastaccessed";

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

      JdbcSnapshotProperties properties = getJdbcSnapshotProperties();

      mockSnapshotTableColumns();
      mockLastAccessedTableColumns();

      JdbcSnapshotCache uut = new JdbcSnapshotCache(properties, dataSource);
      Timer timer = uut.createTimer();
      assertThat(timer).isNotNull();
    }
  }

  @Nested
  class WhenInstantiating {
    @Test
    void test_invalidNameForTable() {
      JdbcSnapshotProperties properties =
          new JdbcSnapshotProperties()
              .setSnapshotTableName("name; drop table")
              .setSnapshotAccessTableName("valid");
      assertThatThrownBy(() -> new JdbcSnapshotCache(properties, dataSource))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Invalid table name");
    }

    @Test
    void test_invalidNameForLastAccessedTable() {
      JdbcSnapshotProperties properties =
          new JdbcSnapshotProperties()
              .setSnapshotTableName("valid")
              .setSnapshotAccessTableName("name; drop table");
      assertThatThrownBy(() -> new JdbcSnapshotCache(properties, dataSource))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Invalid table name");
    }

    @Test
    void test_doNotCreate_tableDoesntExist() throws SQLException {
      when(dataSource.getConnection()).thenReturn(connection);
      when(connection.getMetaData().getTables(any(), any(), any(), any())).thenReturn(resultSet);
      when(resultSet.next()).thenReturn(false);

      JdbcSnapshotProperties properties = getJdbcSnapshotProperties();
      assertThatThrownBy(() -> new JdbcSnapshotCache(properties, dataSource))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Snapshots table does not exist: ");
    }

    @Test
    void test_doNotCreate_snapshotTableIsNotValid() throws SQLException {
      when(dataSource.getConnection()).thenReturn(connection);
      when(connection.getMetaData().getTables(any(), any(), any(), any())).thenReturn(resultSet);
      when(resultSet.next()).thenReturn(true);

      ResultSet columns = mock(ResultSet.class);
      when(connection.getMetaData().getColumns(null, null, TABLE_NAME, null)).thenReturn(columns);
      when(columns.next()).thenReturn(true, false);
      when(columns.getString("COLUMN_NAME")).thenReturn("another");

      JdbcSnapshotProperties properties = getJdbcSnapshotProperties();
      assertThatThrownBy(() -> new JdbcSnapshotCache(properties, dataSource))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining(
              "Snapshot table schema is not compatible with Factus. Table "
                  + TABLE_NAME
                  + " is missing columns: ")
          .hasMessageContaining("projection_class")
          .hasMessageContaining("aggregate_id")
          .hasMessageContaining("last_fact_id")
          .hasMessageContaining("bytes")
          .hasMessageContaining("snapshot_serializer_id");
    }

    @Test
    void test_doNotCreate_lastAccessedTableIsNotValid() throws SQLException {
      when(dataSource.getConnection()).thenReturn(connection);
      when(connection.getMetaData().getTables(any(), any(), any(), any())).thenReturn(resultSet);
      when(resultSet.next()).thenReturn(true);

      mockSnapshotTableColumns();

      ResultSet columns = mock(ResultSet.class);
      when(connection.getMetaData().getColumns(null, null, LAST_ACCESSED_TABLE_NAME, null))
          .thenReturn(columns);
      when(columns.next()).thenReturn(true, false);
      when(columns.getString("COLUMN_NAME")).thenReturn("another");

      JdbcSnapshotProperties properties = getJdbcSnapshotProperties();
      assertThatThrownBy(() -> new JdbcSnapshotCache(properties, dataSource))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining(
              "Snapshot table schema is not compatible with Factus. Table "
                  + LAST_ACCESSED_TABLE_NAME
                  + " is missing columns: ")
          .hasMessageContaining("projection_class")
          .hasMessageContaining("aggregate_id")
          .hasMessageContaining("last_accessed");
    }

    @Test
    void test_doNotCreate_tableIsNotValid_oneColumnMissing() throws SQLException {
      when(dataSource.getConnection()).thenReturn(connection);
      when(connection.getMetaData().getTables(any(), any(), any(), any())).thenReturn(resultSet);
      when(resultSet.next()).thenReturn(true);

      ResultSet columns = mock(ResultSet.class);
      when(connection.getMetaData().getColumns(any(), any(), any(), any())).thenReturn(columns);
      when(columns.next()).thenReturn(true, true, true, true, false);
      when(columns.getString("COLUMN_NAME"))
          .thenReturn("projection_class", "aggregate_id", "last_fact_id", "bytes");

      JdbcSnapshotProperties properties = getJdbcSnapshotProperties();
      assertThatThrownBy(() -> new JdbcSnapshotCache(properties, dataSource))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining(
              "Snapshot table schema is not compatible with Factus. Table "
                  + TABLE_NAME
                  + " is missing columns: ")
          .hasMessageContaining("snapshot_serializer_id");
    }

    @Test
    void test_tableIsValid() throws Exception {
      when(dataSource.getConnection()).thenReturn(connection);
      when(connection.getMetaData().getTables(any(), any(), any(), any())).thenReturn(resultSet);
      when(resultSet.next()).thenReturn(true);

      mockSnapshotTableColumns();
      mockLastAccessedTableColumns();

      LogCaptor logCaptor = LogCaptor.forClass(JdbcSnapshotCache.class);

      assertDoesNotThrow(
          () ->
              new JdbcSnapshotCache(getJdbcSnapshotProperties(), dataSource) {
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

      mockSnapshotTableColumns();
      mockLastAccessedTableColumns();

      LogCaptor logCaptor = LogCaptor.forClass(JdbcSnapshotCache.class);

      JdbcSnapshotProperties properties = getJdbcSnapshotProperties();
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

      mockSnapshotTableColumns();
      mockLastAccessedTableColumns();

      LogCaptor logCaptor = LogCaptor.forClass(JdbcSnapshotCache.class);

      JdbcSnapshotProperties properties = getJdbcSnapshotProperties();
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
    @Mock PreparedStatement lastAccessedPreparedStatement;
    @Mock DatabaseMetaData metaData;
    private JdbcSnapshotCache jdbcSnapshotCache;

    @ProjectionMetaData(revision = 1L)
    class TestSnapshotProjection implements SnapshotProjection {}

    @ProjectionMetaData(revision = 1L)
    class TestAggregateProjection extends Aggregate {}

    @BeforeEach
    @SneakyThrows
    void setUp() {
      when(dataSource.getConnection()).thenReturn(connection);
      when(connection.getMetaData()).thenReturn(metaData);
      when(metaData.getTables(any(), any(), any(), any())).thenReturn(resultSet);
      when(resultSet.next()).thenReturn(true);

      mockSnapshotTableColumns();
      mockLastAccessedTableColumns();
      jdbcSnapshotCache =
          new JdbcSnapshotCache(
              getJdbcSnapshotProperties().setDeleteSnapshotStaleForDays(0), dataSource);
    }

    @Test
    @SneakyThrows
    void setSnapshotViaUpdate() {
      final PreparedStatement updateSnapshot = mock(PreparedStatement.class);
      final PreparedStatement updateLastAccessed = mock(PreparedStatement.class);

      when(dataSource.getConnection()).thenReturn(connection);
      when(connection.prepareStatement(contains(TABLE_NAME))).thenReturn(updateSnapshot);
      when(connection.prepareStatement(contains(LAST_ACCESSED_TABLE_NAME)))
          .thenReturn(updateLastAccessed);

      SnapshotData snap =
          new SnapshotData(
              new byte[] {1, 2, 3}, SnapshotSerializerId.of("random"), UUID.randomUUID());

      when(updateSnapshot.executeUpdate())
          .thenReturn(1); // updating snapshot works, no insert necessary
      when(updateLastAccessed.executeUpdate())
          .thenReturn(1); // updating last accessed works, no insert necessary

      // when
      jdbcSnapshotCache.store(SnapshotIdentifier.of(TestSnapshotProjection.class), snap);

      ArgumentCaptor<String> string = ArgumentCaptor.forClass(String.class);
      ArgumentCaptor<byte[]> bytes = ArgumentCaptor.forClass(byte[].class);

      verify(updateSnapshot).executeUpdate();
      verify(updateSnapshot, times(4)).setString(any(Integer.class), string.capture());
      assertThat(string.getAllValues())
          .containsExactly(
              snap.lastFactId().toString(),
              "random".toLowerCase(),
              ScopedName.fromProjectionMetaData(TestSnapshotProjection.class).asString(),
              null);

      verify(updateSnapshot, times(1)).setBytes(any(Integer.class), bytes.capture());
      assertThat(bytes.getValue()).isEqualTo(snap.serializedProjection());

      // Assert update of last accessed timestamp
      ArgumentCaptor<String> lastAccessedKeys = ArgumentCaptor.forClass(String.class);
      ArgumentCaptor<Timestamp> lastAccessedTimestamp = ArgumentCaptor.forClass(Timestamp.class);

      verify(updateLastAccessed).executeUpdate();
      verify(updateLastAccessed, times(2))
          .setString(any(Integer.class), lastAccessedKeys.capture());
      assertThat(lastAccessedKeys.getAllValues())
          .containsExactly(
              ScopedName.fromProjectionMetaData(TestSnapshotProjection.class).asString(), null);

      verify(updateLastAccessed, times(1))
          .setTimestamp(any(Integer.class), lastAccessedTimestamp.capture());
      assertThat(lastAccessedTimestamp.getValue())
          .isEqualTo(Timestamp.valueOf(LocalDate.now().atStartOfDay()));
    }

    @Test
    @SneakyThrows
    void setSnapshotViaInsert() {
      final PreparedStatement updateSnapshot = mock(PreparedStatement.class);
      final PreparedStatement insertSnapshot = mock(PreparedStatement.class);
      final PreparedStatement updateLastAccessed = mock(PreparedStatement.class);
      final PreparedStatement insertLastAccessed = mock(PreparedStatement.class);

      when(dataSource.getConnection()).thenReturn(connection);
      when(connection.prepareStatement(contains(TABLE_NAME)))
          .thenReturn(updateSnapshot, insertSnapshot);
      when(connection.prepareStatement(contains(LAST_ACCESSED_TABLE_NAME)))
          .thenReturn(updateLastAccessed, insertLastAccessed);

      SnapshotData snap =
          new SnapshotData(
              new byte[] {1, 2, 3}, SnapshotSerializerId.of("random"), UUID.randomUUID());

      when(updateSnapshot.executeUpdate()).thenReturn(0); // updating snapshot fails
      when(insertSnapshot.executeUpdate()).thenReturn(1); // continue with insert
      when(updateLastAccessed.executeUpdate()).thenReturn(0); // updating lastAccessed fails
      when(insertLastAccessed.executeUpdate()).thenReturn(1); // continue with insert

      // when
      jdbcSnapshotCache.store(SnapshotIdentifier.of(TestSnapshotProjection.class), snap);

      ArgumentCaptor<String> stringsInUpdate = ArgumentCaptor.forClass(String.class);
      ArgumentCaptor<byte[]> bytesInUpdate = ArgumentCaptor.forClass(byte[].class);

      // verify update statement
      verify(updateSnapshot).executeUpdate();
      verify(updateSnapshot, times(4)).setString(any(Integer.class), stringsInUpdate.capture());
      assertThat(stringsInUpdate.getAllValues())
          .containsExactly(
              snap.lastFactId().toString(),
              "random".toLowerCase(),
              ScopedName.fromProjectionMetaData(TestSnapshotProjection.class).asString(),
              null);

      verify(updateSnapshot, times(1)).setBytes(any(Integer.class), bytesInUpdate.capture());
      assertThat(bytesInUpdate.getValue()).isEqualTo(snap.serializedProjection());

      // verify insert statement
      verify(insertSnapshot).executeUpdate();
      ArgumentCaptor<String> stringsInInsert = ArgumentCaptor.forClass(String.class);
      ArgumentCaptor<byte[]> bytesInInsert = ArgumentCaptor.forClass(byte[].class);

      verify(insertSnapshot, times(4)).setString(any(Integer.class), stringsInInsert.capture());
      assertThat(stringsInInsert.getAllValues())
          .containsExactly(
              ScopedName.fromProjectionMetaData(TestSnapshotProjection.class).asString(),
              null,
              snap.lastFactId().toString(),
              "random");

      verify(insertSnapshot, times(1)).setBytes(any(Integer.class), bytesInInsert.capture());
      assertThat(bytesInInsert.getValue()).isEqualTo(snap.serializedProjection());

      // Assert update of last accessed timestamp
      ArgumentCaptor<String> lastAccessedKeysForUpdate = ArgumentCaptor.forClass(String.class);
      ArgumentCaptor<Timestamp> lastAccessedTimestampForUpdate =
          ArgumentCaptor.forClass(Timestamp.class);

      verify(updateLastAccessed).executeUpdate();
      verify(updateLastAccessed, times(2))
          .setString(any(Integer.class), lastAccessedKeysForUpdate.capture());
      assertThat(lastAccessedKeysForUpdate.getAllValues())
          .containsExactly(
              ScopedName.fromProjectionMetaData(TestSnapshotProjection.class).asString(), null);

      verify(updateLastAccessed, times(1))
          .setTimestamp(any(Integer.class), lastAccessedTimestampForUpdate.capture());
      assertThat(lastAccessedTimestampForUpdate.getValue())
          .isEqualTo(Timestamp.valueOf(LocalDate.now().atStartOfDay()));

      // Assert insert of last accessed timestamp
      ArgumentCaptor<String> lastAccessedKeysForInsert = ArgumentCaptor.forClass(String.class);
      ArgumentCaptor<Timestamp> lastAccessedTimestampForInsert =
          ArgumentCaptor.forClass(Timestamp.class);

      verify(updateLastAccessed).executeUpdate();
      verify(updateLastAccessed, times(2))
          .setString(any(Integer.class), lastAccessedKeysForInsert.capture());
      assertThat(lastAccessedKeysForInsert.getAllValues())
          .containsExactly(
              ScopedName.fromProjectionMetaData(TestSnapshotProjection.class).asString(), null);

      verify(updateLastAccessed, times(1))
          .setTimestamp(any(Integer.class), lastAccessedTimestampForInsert.capture());
      assertThat(lastAccessedTimestampForInsert.getValue())
          .isEqualTo(Timestamp.valueOf(LocalDate.now().atStartOfDay()));
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
          .hasMessage("Failed to insert snapshot into database. SnapshotId: " + id);

      // at least try
      verify(update).executeUpdate();
      verify(insert).executeUpdate();

      verifyNoInteractions(lastAccessedPreparedStatement);
    }

    @Test
    @SneakyThrows
    void clearSnapshot() {
      when(dataSource.getConnection()).thenReturn(connection);
      when(connection.prepareStatement(contains(TABLE_NAME))).thenReturn(preparedStatement);
      when(connection.prepareStatement(contains(LAST_ACCESSED_TABLE_NAME)))
          .thenReturn(lastAccessedPreparedStatement);

      jdbcSnapshotCache.remove(SnapshotIdentifier.of(TestSnapshotProjection.class));

      ArgumentCaptor<String> string = ArgumentCaptor.forClass(String.class);

      verify(preparedStatement, times(2)).setString(any(Integer.class), string.capture());
      verify(preparedStatement, times(1)).executeUpdate();

      assertThat(string.getAllValues())
          .containsExactly(
              ScopedName.fromProjectionMetaData(TestSnapshotProjection.class).asString(), null);

      // Assert removal of last accessed timestamp
      verify(lastAccessedPreparedStatement, times(1)).executeUpdate();
      ArgumentCaptor<String> lastAccessedKeys = ArgumentCaptor.forClass(String.class);
      verify(lastAccessedPreparedStatement, times(2))
          .setString(any(Integer.class), lastAccessedKeys.capture());
      verify(lastAccessedPreparedStatement, times(1)).executeUpdate();

      assertThat(lastAccessedKeys.getAllValues())
          .containsExactly(
              ScopedName.fromProjectionMetaData(TestSnapshotProjection.class).asString(), null);

      verify(connection).commit();
    }

    @Test
    @SneakyThrows
    void clearSnapshot_rollbackOnFailure() {
      when(dataSource.getConnection()).thenReturn(connection);
      when(connection.getAutoCommit()).thenReturn(true);
      when(connection.prepareStatement(contains(TABLE_NAME))).thenReturn(preparedStatement);
      when(connection.prepareStatement(contains(LAST_ACCESSED_TABLE_NAME)))
          .thenReturn(lastAccessedPreparedStatement);
      when(preparedStatement.executeUpdate()).thenThrow(new SQLException("failure"));

      assertThatThrownBy(
              () -> jdbcSnapshotCache.remove(SnapshotIdentifier.of(TestSnapshotProjection.class)))
          .isInstanceOf(SQLException.class);

      verify(connection).rollback();
      verify(connection).setAutoCommit(true);
    }

    @Test
    @SneakyThrows
    void getSnapshot() {
      when(dataSource.getConnection()).thenReturn(connection);
      when(connection.prepareStatement(contains(TABLE_NAME))).thenReturn(preparedStatement);
      when(preparedStatement.executeQuery()).thenReturn(resultSet);
      when(resultSet.next()).thenReturn(true);
      when(connection.prepareStatement(contains(LAST_ACCESSED_TABLE_NAME)))
          .thenReturn(lastAccessedPreparedStatement);
      when(lastAccessedPreparedStatement.executeUpdate())
          .thenReturn(1); // means: updating lastAccessed works, no insert necessary

      UUID lastFactId = UUID.randomUUID();
      byte[] bytes = {1, 2, 3};

      when(resultSet.getBytes(1)).thenReturn(bytes);
      when(resultSet.getString(2)).thenReturn("serializerId");
      when(resultSet.getString(3)).thenReturn(lastFactId.toString());

      JdbcSnapshotCache uut = spy(jdbcSnapshotCache);
      SnapshotIdentifier id = SnapshotIdentifier.of(TestSnapshotProjection.class);

      // when
      SnapshotData snapshot = uut.find(id).get();

      assertThat(snapshot.lastFactId()).isEqualTo(lastFactId);
      assertThat(snapshot.snapshotSerializerId().name()).isEqualTo("serializerId");
      assertThat(snapshot.serializedProjection()).isEqualTo(bytes);

      ArgumentCaptor<String> string = ArgumentCaptor.forClass(String.class);

      verify(preparedStatement, times(2)).setString(any(Integer.class), string.capture());
      verify(preparedStatement, times(1)).executeQuery();
      assertThat(string.getAllValues())
          .containsExactly(
              ScopedName.fromProjectionMetaData(TestSnapshotProjection.class).asString(), null);

      // Wait for async update of lastAccessed timestamp.
      await()
          .atMost(2, TimeUnit.SECONDS)
          .untilAsserted(
              () -> {
                verify(uut, times(1)).updateLastAccessedTime(id);

                ArgumentCaptor<String> lastAccessedKeys = ArgumentCaptor.forClass(String.class);
                verify(lastAccessedPreparedStatement, times(2))
                    .setString(any(Integer.class), lastAccessedKeys.capture());
                verify(lastAccessedPreparedStatement, times(1)).executeUpdate();
                assertThat(lastAccessedKeys.getAllValues())
                    .containsExactly(
                        ScopedName.fromProjectionMetaData(TestSnapshotProjection.class).asString(),
                        null);

                verify(lastAccessedPreparedStatement, times(1)).executeUpdate();
              });
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

    @Nested
    class WhenResolvingMetadataIdentifierNormalizer {

      // unknown leave
      // exception -> leave

      @Test
      @SneakyThrows
      void storesLowerCase() {
        when(metaData.storesLowerCaseIdentifiers()).thenReturn(true);

        final UnaryOperator<String> normalizer = jdbcSnapshotCache.resolveIdentifierNormalizer();
        assertThat(normalizer.apply("foo")).isEqualTo("foo");
        assertThat(normalizer.apply("FOO")).isEqualTo("foo");
        assertThat(normalizer.apply("FoO")).isEqualTo("foo");
      }

      @Test
      @SneakyThrows
      void storesUpperCase() {
        when(metaData.storesUpperCaseIdentifiers()).thenReturn(true);

        final UnaryOperator<String> normalizer = jdbcSnapshotCache.resolveIdentifierNormalizer();
        assertThat(normalizer.apply("foo")).isEqualTo("FOO");
        assertThat(normalizer.apply("FOO")).isEqualTo("FOO");
        assertThat(normalizer.apply("FoO")).isEqualTo("FOO");
      }

      @Test
      @SneakyThrows
      void storesUnknown() {
        final UnaryOperator<String> normalizer = jdbcSnapshotCache.resolveIdentifierNormalizer();
        assertThat(normalizer.apply("foo")).isEqualTo("foo");
        assertThat(normalizer.apply("FOO")).isEqualTo("FOO");
        assertThat(normalizer.apply("FoO")).isEqualTo("FoO");
      }

      @Test
      @SneakyThrows
      void sqlExceptionWhileResolving() {
        when(metaData.storesLowerCaseIdentifiers()).thenThrow(new SQLException("nope"));

        final UnaryOperator<String> normalizer = jdbcSnapshotCache.resolveIdentifierNormalizer();
        assertThat(normalizer.apply("foo")).isEqualTo("foo");
        assertThat(normalizer.apply("FOO")).isEqualTo("FOO");
        assertThat(normalizer.apply("FoO")).isEqualTo("FoO");
      }
    }
  }

  private static JdbcSnapshotProperties getJdbcSnapshotProperties() {
    return new JdbcSnapshotProperties()
        .setSnapshotTableName(TABLE_NAME)
        .setSnapshotAccessTableName(LAST_ACCESSED_TABLE_NAME);
  }

  private void mockSnapshotTableColumns() throws SQLException {
    ResultSet columns = mock(ResultSet.class);
    when(connection.getMetaData().getColumns(null, null, TABLE_NAME, null)).thenReturn(columns);
    when(columns.next()).thenReturn(true, true, true, true, true, false);
    when(columns.getString("COLUMN_NAME"))
        .thenReturn(
            "projection_class", "aggregate_id", "last_fact_id", "bytes", "snapshot_serializer_id");
  }

  private void mockLastAccessedTableColumns() throws SQLException {
    ResultSet columns = mock(ResultSet.class);
    when(connection.getMetaData().getColumns(null, null, LAST_ACCESSED_TABLE_NAME, null))
        .thenReturn(columns);
    when(columns.next()).thenReturn(true, true, true, false);
    when(columns.getString("COLUMN_NAME"))
        .thenReturn("projection_class", "aggregate_id", "last_accessed");
  }

  @ProjectionMetaData(name = "hugo", revision = 1)
  static class MyAgg extends Aggregate {}
}
