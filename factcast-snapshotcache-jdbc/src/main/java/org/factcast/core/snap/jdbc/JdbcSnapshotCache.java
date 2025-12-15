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
package org.factcast.core.snap.jdbc;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.factcast.factus.projection.ScopedName;
import org.factcast.factus.serializer.SnapshotSerializerId;
import org.factcast.factus.snapshot.SnapshotCache;
import org.factcast.factus.snapshot.SnapshotData;
import org.factcast.factus.snapshot.SnapshotIdentifier;

@Slf4j
public class JdbcSnapshotCache implements SnapshotCache {
  public static final String VALIDATION_REGEX = "^\\w+$";
  public final String queryStatement;
  public final String mergeStatement;
  public final String updateLastAccessedStatement;
  public final String deleteStatement;
  public final String deleteLastAccessedStatement;
  private final DataSource dataSource;

  public JdbcSnapshotCache(JdbcSnapshotProperties properties, DataSource dataSource) {
    this.dataSource = dataSource;

    String tableName = properties.getSnapshotTableName();
    String lastAccessedTableName = properties.getSnapshotLastAccessedTableName();

    if (!tableName.matches(VALIDATION_REGEX) || !lastAccessedTableName.matches(VALIDATION_REGEX)) {
      throw new IllegalArgumentException("Invalid table name.");
    }

    queryStatement =
        "SELECT bytes, snapshot_serializer_id, last_fact_id FROM "
            + tableName
            + " WHERE projection_class = ? AND aggregate_id = ?";

    mergeStatement =
        "MERGE INTO "
            + tableName
            + " USING (VALUES (?, ?, ?, ?, ?)) as new (_projection_class, _aggregate_id, _last_fact_id, _bytes, _snapshot_serializer_id)"
            + " ON projection_class=_projection_class AND aggregate_id=_aggregate_id"
            + " WHEN MATCHED THEN"
            + " UPDATE SET last_fact_id=_last_fact_id, bytes=_bytes, snapshot_serializer_id=_snapshot_serializer_id"
            + " WHEN NOT MATCHED THEN"
            + " INSERT VALUES (_projection_class, _aggregate_id, _last_fact_id, _bytes, _snapshot_serializer_id)";

    deleteStatement =
        "DELETE FROM " + tableName + " WHERE projection_class = ? AND aggregate_id = ?";

    updateLastAccessedStatement =
        "MERGE INTO "
            + lastAccessedTableName
            + " USING (SELECT ? AS _projection_class, ? AS _aggregate_id, ? as _last_accessed)"
            + " ON projection_class=_projection_class AND aggregate_id=_aggregate_id"
            + " WHEN MATCHED THEN"
            + " UPDATE SET last_accessed=_last_accessed"
            + " WHEN NOT MATCHED THEN"
            + " INSERT (projection_class, aggregate_id, last_accessed) VALUES (_projection_class, _aggregate_id, _last_accessed)";

    deleteLastAccessedStatement =
        "DELETE FROM " + lastAccessedTableName + " WHERE projection_class = ? AND aggregate_id = ?";

    boolean snapTableExists = doesTableExist(tableName);
    boolean lastAccessedTableExists = doesTableExist(lastAccessedTableName);

    if (!snapTableExists || !lastAccessedTableExists) {
      throw new IllegalStateException(
          "Snapshots table does not exist: "
              + (snapTableExists ? lastAccessedTableName : tableName));
    } else {
      validateColumns(tableName, lastAccessedTableName);
    }

    if (properties.getDeleteSnapshotStaleForDays() > 0) {
      createTimer()
          .scheduleAtFixedRate(
              new StaleSnapshotsTimerTask(
                  dataSource,
                  tableName,
                  lastAccessedTableName,
                  properties.getDeleteSnapshotStaleForDays()),
              0,
              TimeUnit.DAYS.toMillis(1));
    } else {
      log.info("Scheduled Snapshot cleanup is disabled");
    }
  }

  // exists in order to be able to hook into during unit tests
  protected Timer createTimer() {
    // we want to be able to identify it and do not want a dangling service on shutdown
    return new Timer("JdbcSnapshotCache", true);
  }

  @SneakyThrows
  public boolean doesTableExist(String tableName) {
    try (Connection connection = dataSource.getConnection();
        ResultSet rs =
            connection.getMetaData().getTables(null, null, tableName, new String[] {"TABLE"})) {

      return rs.next();
    }
  }

  @SneakyThrows
  public void validateColumns(String snapshotTableName, String lastAccessedTableName) {
    validateColumnsOnTable(
        snapshotTableName,
        Sets.newHashSet(
            "projection_class", "aggregate_id", "last_fact_id", "bytes", "snapshot_serializer_id"));
    validateColumnsOnTable(
        lastAccessedTableName,
        Sets.newHashSet("projection_class", "aggregate_id", "last_accessed"));
  }

  private void validateColumnsOnTable(String tableName, HashSet<String> columnsSet)
      throws SQLException {
    try (Connection connection = dataSource.getConnection();
        ResultSet columns = connection.getMetaData().getColumns(null, null, tableName, null)) {

      while (columns.next()) {
        String columnName = columns.getString("COLUMN_NAME");

        columnsSet.remove(columnName);
      }
      if (!columnsSet.isEmpty()) {
        throw new IllegalStateException(
            String.format(
                "Snapshot table schema is not compatible with Factus. Table %s is missing columns: %s",
                tableName, columnsSet));
      }
    }
  }

  @Override
  @SneakyThrows
  public @NonNull Optional<SnapshotData> find(@NonNull SnapshotIdentifier id) {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(queryStatement)) {
      statement.setString(1, createKeyFor(id));
      statement.setString(2, id.aggregateId() != null ? id.aggregateId().toString() : null);
      final ResultSet resultSet = statement.executeQuery();
      if (resultSet.next()) {
        SnapshotData snapshot =
            new SnapshotData(
                resultSet.getBytes(1),
                SnapshotSerializerId.of(resultSet.getString(2)),
                UUID.fromString(resultSet.getString(3)));

        // update last accessed
        updateLastAccessedTime(id);
        return Optional.of(snapshot);
      }
    }
    return Optional.empty();
  }

  @VisibleForTesting
  String createKeyFor(SnapshotIdentifier id) {
    return ScopedName.fromProjectionMetaData(id.projectionClass()).asString();
  }

  /** Updates or creates the lastAccessed timestamp if it doesn't exist or equal today's' date. */
  @VisibleForTesting
  protected void updateLastAccessedTime(@NonNull SnapshotIdentifier id) {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(updateLastAccessedStatement)) {
      statement.setString(1, createKeyFor(id));
      statement.setString(2, id.aggregateId() != null ? id.aggregateId().toString() : null);
      statement.setTimestamp(3, Timestamp.valueOf(LocalDate.now().atStartOfDay()));
      statement.executeUpdate();
    } catch (Exception e) {
      log.error("Failed to update last accessed time for snapshot {}", id, e);
    }
  }

  @Override
  @SneakyThrows
  public void store(@NonNull SnapshotIdentifier id, @NonNull SnapshotData snapshot) {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(mergeStatement)) {
      statement.setString(1, createKeyFor(id));
      statement.setString(2, id.aggregateId() != null ? id.aggregateId().toString() : null);
      statement.setString(3, snapshot.lastFactId().toString());
      statement.setBytes(4, snapshot.serializedProjection());
      statement.setString(5, snapshot.snapshotSerializerId().name());
      if (statement.executeUpdate() == 0) {
        throw new IllegalStateException(
            "Failed to insert snapshot into database. SnapshotId: " + id);
      } else {
        updateLastAccessedTime(id);
      }
    }
  }

  @Override
  @SneakyThrows
  public void remove(@NonNull SnapshotIdentifier id) {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement snapshotStatement = connection.prepareStatement(deleteStatement);
        PreparedStatement lastAccessStatement =
            connection.prepareStatement(deleteLastAccessedStatement)) {
      final String key = createKeyFor(id);
      final String aggId = id.aggregateId() != null ? id.aggregateId().toString() : null;
      snapshotStatement.setString(1, key);
      snapshotStatement.setString(2, aggId);
      snapshotStatement.executeUpdate();
      lastAccessStatement.setString(1, key);
      lastAccessStatement.setString(2, aggId);
      lastAccessStatement.executeUpdate();
    }
  }
}
