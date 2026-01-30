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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.Timer;
import java.util.UUID;
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
  public final String agnosticMergeStatementUpdatePart;
  public final String agnosticMergeStatementInsertPart;
  public final String updateLastAccessedStatement;
  public final String deleteStatement;
  private final DataSource dataSource;

  public JdbcSnapshotCache(JdbcSnapshotProperties properties, DataSource dataSource) {
    this.dataSource = dataSource;

    String tableName = properties.getSnapshotTableName();

    if (!tableName.matches(VALIDATION_REGEX)) {
      throw new IllegalArgumentException("Invalid table name.");
    }

    queryStatement =
        "SELECT bytes, snapshot_serializer_id, last_fact_id FROM "
            + tableName
            + " WHERE projection_class = ? AND aggregate_id = ?";

    // part 1 of a replacement for a "MERGE INTO" statement which is not supported by MySQL
    agnosticMergeStatementUpdatePart =
        "UPDATE "
            + tableName
            + " SET last_fact_id = ?,"
            + "    bytes = ?,"
            + "    snapshot_serializer_id = ?,"
            + "    last_accessed = ?"
            + " WHERE projection_class = ?"
            + "  AND aggregate_id = ?;";

    // part 2 of a replacement for a "MERGE INTO" statement which is not supported by MySQL
    agnosticMergeStatementInsertPart =
        " INSERT INTO "
            + tableName
            + " (projection_class, aggregate_id, last_fact_id, bytes, snapshot_serializer_id, last_accessed)"
            + " VALUES (?, ?, ?, ?, ?, ?);";

    deleteStatement =
        "DELETE FROM " + tableName + " WHERE projection_class = ? AND aggregate_id = ?";

    updateLastAccessedStatement =
        "UPDATE "
            + tableName
            + " SET last_accessed = ? WHERE projection_class = ? AND aggregate_id = ?";

    boolean snapTableExists = doesTableExist(tableName);

    if (!snapTableExists) {
      throw new IllegalStateException("Snapshots table does not exist: " + tableName);
    } else {
      validateColumns(tableName);
    }

    if (properties.getDeleteSnapshotStaleForDays() > 0) {
      createTimer()
          .scheduleAtFixedRate(
              new StaleSnapshotsTimerTask(
                  dataSource, tableName, properties.getDeleteSnapshotStaleForDays()),
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

      return Boolean.TRUE.equals(rs.next());
    }
  }

  @SneakyThrows
  public void validateColumns(String tableName) {
    try (Connection connection = dataSource.getConnection();
        ResultSet columns = connection.getMetaData().getColumns(null, null, tableName, null)) {

      Set<String> columnsSet =
          Sets.newHashSet(
              "projection_class",
              "aggregate_id",
              "last_fact_id",
              "bytes",
              "snapshot_serializer_id",
              "last_accessed");
      while (columns.next()) {
        String columnName = columns.getString("COLUMN_NAME");

        columnsSet.remove(columnName);
      }
      if (!columnsSet.isEmpty()) {
        throw new IllegalStateException(
            "Snapshot table schema is not compatible with Factus. Missing columns: " + columnsSet);
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
      try (ResultSet resultSet = statement.executeQuery()) {
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
    }

    return Optional.empty();
  }

  @VisibleForTesting
  String createKeyFor(SnapshotIdentifier id) {
    return ScopedName.fromProjectionMetaData(id.projectionClass()).asString();
  }

  @VisibleForTesting
  protected void updateLastAccessedTime(@NonNull SnapshotIdentifier id) {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(updateLastAccessedStatement)) {
      statement.setTimestamp(1, Timestamp.valueOf(LocalDate.now().atStartOfDay()));
      statement.setString(2, createKeyFor(id));
      statement.setString(3, id.aggregateId() != null ? id.aggregateId().toString() : null);
      statement.executeUpdate();
    } catch (Exception e) {
      log.error("Failed to update last accessed time for snapshot {}", id, e);
    }
  }

  @Override
  @SneakyThrows
  public void store(@NonNull SnapshotIdentifier id, @NonNull SnapshotData snapshot) {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement storeUpdate =
            connection.prepareStatement(agnosticMergeStatementUpdatePart);
        PreparedStatement storeInsert =
            connection.prepareStatement(agnosticMergeStatementInsertPart)) {

      connection.setAutoCommit(false);

      final String lastFactId = snapshot.lastFactId().toString();
      final byte[] bytes = snapshot.serializedProjection();
      final String snapshotSerializerId = snapshot.snapshotSerializerId().name();
      final Timestamp lastAccessed = Timestamp.valueOf(LocalDate.now().atStartOfDay());
      final String projectionClass = createKeyFor(id);
      final String aggIdOrNull = id.aggregateId() != null ? id.aggregateId().toString() : null;

      // try update first...
      storeUpdate.setString(1, lastFactId);
      storeUpdate.setBytes(2, bytes);
      storeUpdate.setString(3, snapshotSerializerId);
      storeUpdate.setTimestamp(4, lastAccessed);
      storeUpdate.setString(5, projectionClass);
      storeUpdate.setString(6, aggIdOrNull);

      if (storeUpdate.executeUpdate() == 0) {
        // nothing to update? ... then just insert!
        storeInsert.setString(1, projectionClass);
        storeInsert.setString(2, aggIdOrNull);
        storeInsert.setString(3, lastFactId);
        storeInsert.setBytes(4, bytes);
        storeInsert.setString(5, snapshotSerializerId);
        storeInsert.setTimestamp(6, lastAccessed);

        if (storeInsert.executeUpdate() == 0) {
          connection.rollback();
          throw new IllegalStateException(
              "Failed to insert snapshot into database. SnapshotId: " + id);
        }
      }
      connection.commit();
    }
  }

  @Override
  @SneakyThrows
  public void remove(@NonNull SnapshotIdentifier id) {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(deleteStatement)) {
      statement.setString(1, createKeyFor(id));
      statement.setString(2, id.aggregateId() != null ? id.aggregateId().toString() : null);
      statement.executeUpdate();
    }
  }
}
