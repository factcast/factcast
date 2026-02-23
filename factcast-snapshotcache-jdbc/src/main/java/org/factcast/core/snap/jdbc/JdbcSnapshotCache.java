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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
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
  public final String snapshotUpdateStatement;
  public final String snapshotInsertStatement;
  public final String lastAccessedUpdateStatement;
  public final String lastAccessedInsertStatement;

  public final String deleteStatement;
  public final String deleteLastAccessedStatement;
  private final DataSource dataSource;
  private final Function<String, String> identifierNormalizer;

  private final ExecutorService lastAccessedUpdateExecutor =
      Executors.newSingleThreadExecutor(
          r -> {
            Thread t = new Thread(r, "JdbcSnapshotCache-lastAccessedUpdater");
            t.setDaemon(true);
            return t;
          });

  public JdbcSnapshotCache(JdbcSnapshotProperties properties, DataSource dataSource) {
    this.dataSource = dataSource;

    String tableName = properties.getSnapshotTableName();
    String lastAccessedTableName = properties.getSnapshotAccessTableName();

    if (!tableName.matches(VALIDATION_REGEX) || !lastAccessedTableName.matches(VALIDATION_REGEX)) {
      throw new IllegalArgumentException("Invalid table name.");
    }

    queryStatement =
        "SELECT bytes, snapshot_serializer_id, last_fact_id FROM "
            + tableName
            + " WHERE projection_class = ? AND aggregate_id = ?";

    // part 1 of a replacement for a "MERGE INTO" snapshot which is not supported by all SQL flavors
    snapshotUpdateStatement =
        "UPDATE "
            + tableName
            + " SET last_fact_id = ?,"
            + "    bytes = ?,"
            + "    snapshot_serializer_id = ?"
            + " WHERE projection_class = ?"
            + "  AND aggregate_id = ?";

    // part 2 of a replacement for a "MERGE INTO" snapshot which is not supported by all SQL flavors
    snapshotInsertStatement =
        " INSERT INTO "
            + tableName
            + " (projection_class, aggregate_id, last_fact_id, bytes, snapshot_serializer_id)"
            + " VALUES (?, ?, ?, ?, ?)";

    deleteStatement =
        "DELETE FROM " + tableName + " WHERE projection_class = ? AND aggregate_id = ?";

    // part 1 of a replacement for a "MERGE INTO" lastAccessed which is not supported by all SQL
    // flavors
    lastAccessedUpdateStatement =
        "UPDATE "
            + lastAccessedTableName
            + " SET last_accessed = ?"
            + " WHERE projection_class = ?"
            + "  AND aggregate_id = ?";

    // part 2 of a replacement for a "MERGE INTO" lastAccessed which is not supported by all SQL
    // flavors
    lastAccessedInsertStatement =
        " INSERT INTO "
            + lastAccessedTableName
            + " (projection_class, aggregate_id, last_accessed)"
            + " VALUES (?, ?, ?)";

    deleteLastAccessedStatement =
        "DELETE FROM " + lastAccessedTableName + " WHERE projection_class = ? AND aggregate_id = ?";

    identifierNormalizer = resolveIdentifierNormalizer();

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
  public boolean doesTableExist(@NonNull String tableName) {
    try (Connection connection = dataSource.getConnection();
        ResultSet rs =
            connection
                .getMetaData()
                .getTables(
                    null, null, identifierNormalizer.apply(tableName), new String[] {"TABLE"})) {
      return rs.next();
    }
  }

  @SneakyThrows
  public void validateColumns(String snapshotTableName, String lastAccessedTableName) {
    validateColumnsOnTable(
        identifierNormalizer.apply(snapshotTableName),
        Sets.newHashSet(
                "projection_class",
                "aggregate_id",
                "last_fact_id",
                "bytes",
                "snapshot_serializer_id")
            .stream()
            .map(identifierNormalizer)
            .collect(Collectors.toSet()));

    validateColumnsOnTable(
        identifierNormalizer.apply(lastAccessedTableName),
        Sets.newHashSet("projection_class", "aggregate_id", "last_accessed").stream()
            .map(identifierNormalizer)
            .collect(Collectors.toSet()));
  }

  private void validateColumnsOnTable(String tableName, Set<String> columnsSet)
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
        lastAccessedUpdateExecutor.execute(() -> updateLastAccessedTime(id));
        return Optional.of(snapshot);
      }
    }
    return Optional.empty();
  }

  @VisibleForTesting
  String createKeyFor(SnapshotIdentifier id) {
    return ScopedName.fromProjectionMetaData(id.projectionClass()).asString();
  }

  /** Updates or creates the lastAccessed timestamp if it doesn't exist or equal today's date. */
  @VisibleForTesting
  protected void updateLastAccessedTime(@NonNull SnapshotIdentifier id) {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement update = connection.prepareStatement(lastAccessedUpdateStatement);
        PreparedStatement insert = connection.prepareStatement(lastAccessedInsertStatement)) {

      // TODO sometimes nested (not: via find, yes: via store)!!!
      connection.setAutoCommit(false);

      final String projectionClass = createKeyFor(id);
      final String aggIdOrNull = id.aggregateId() != null ? id.aggregateId().toString() : null;
      final Timestamp nowAtStartOfDay = Timestamp.valueOf(LocalDate.now().atStartOfDay());

      // try update first...
      update.setTimestamp(1, nowAtStartOfDay);
      update.setString(2, projectionClass);
      update.setString(3, aggIdOrNull);

      if (update.executeUpdate() == 0) {
        // nothing to update? ... then just insert!
        insert.setString(1, projectionClass);
        insert.setString(2, aggIdOrNull);
        insert.setTimestamp(3, nowAtStartOfDay);
        if (insert.executeUpdate() == 0) {
          connection.rollback();
          throw new IllegalStateException(
              "Failed to update last accessed time for snapshot: " + id);
        }
      }
      // TODO sometimes nested!!! same as above
      connection.commit();
    } catch (Exception e) {
      log.error("Failed to update last accessed time for snapshot {}", id, e);
    }
  }

  @Override
  @SneakyThrows
  public void store(@NonNull SnapshotIdentifier id, @NonNull SnapshotData snapshot) {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement update = connection.prepareStatement(snapshotUpdateStatement);
        PreparedStatement insert = connection.prepareStatement(snapshotInsertStatement)) {

      connection.setAutoCommit(false);

      final String lastFactId = snapshot.lastFactId().toString();
      final byte[] bytes = snapshot.serializedProjection();
      final String snapshotSerializerId = snapshot.snapshotSerializerId().name();
      final String projectionClass = createKeyFor(id);
      final String aggIdOrNull = id.aggregateId() != null ? id.aggregateId().toString() : null;

      // try update first...
      update.setString(1, lastFactId);
      update.setBytes(2, bytes);
      update.setString(3, snapshotSerializerId);
      update.setString(4, projectionClass);
      update.setString(5, aggIdOrNull);

      if (update.executeUpdate() == 0) {
        // nothing to update? ... then just insert!
        insert.setString(1, projectionClass);
        insert.setString(2, aggIdOrNull);
        insert.setString(3, lastFactId);
        insert.setBytes(4, bytes);
        insert.setString(5, snapshotSerializerId);

        if (insert.executeUpdate() == 0) {
          connection.rollback();
          throw new IllegalStateException(
              "Failed to insert snapshot into database. SnapshotId: " + id);
        }
      }
      updateLastAccessedTime(id);
      connection.commit();
    }
  }

  @Override
  @SneakyThrows
  public void remove(@NonNull SnapshotIdentifier id) {
    try (Connection connection = dataSource.getConnection()) {
      final boolean previousAutoCommit = connection.getAutoCommit();
      connection.setAutoCommit(false);
      try (PreparedStatement snapshotStatement = connection.prepareStatement(deleteStatement);
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
        connection.commit();
      } catch (Exception e) {
        connection.rollback();
        throw e;
      } finally {
        connection.setAutoCommit(previousAutoCommit);
      }
    }
  }

  // TODO test
  @SneakyThrows
  @VisibleForTesting
  Function<String, String> resolveIdentifierNormalizer() {
    try (Connection connection = dataSource.getConnection()) {
      if (connection.getMetaData().storesUpperCaseIdentifiers()) return String::toUpperCase;
      if (connection.getMetaData().storesLowerCaseIdentifiers()) return String::toLowerCase;
      log.debug(
          "meta data identifiers stored neither in lower nor UPPER case, not using any modifying identity normalizer");
      return s -> s;
    }
  }
}
