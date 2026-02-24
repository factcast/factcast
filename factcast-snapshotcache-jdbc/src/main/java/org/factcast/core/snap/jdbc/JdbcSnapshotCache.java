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
import java.util.function.UnaryOperator;
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
  private final UnaryOperator<String> identifierNormalizer;

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

    // for updating snapshots, "MERGE INTO" alternative part 1, to support more SQL flavors
    snapshotUpdateStatement =
        "UPDATE "
            + tableName
            + " SET last_fact_id = ?,"
            + "    bytes = ?,"
            + "    snapshot_serializer_id = ?"
            + " WHERE projection_class = ?"
            + "  AND aggregate_id = ?";

    // for updating snapshots, "MERGE INTO" alternative part 2, to support more SQL flavors
    snapshotInsertStatement =
        " INSERT INTO "
            + tableName
            + " (projection_class, aggregate_id, last_fact_id, bytes, snapshot_serializer_id)"
            + " VALUES (?, ?, ?, ?, ?)";

    deleteStatement =
        "DELETE FROM " + tableName + " WHERE projection_class = ? AND aggregate_id = ?";

    // for updating lastAccessed, "MERGE INTO" alternative part 1, to support more SQL flavors
    lastAccessedUpdateStatement =
        "UPDATE "
            + lastAccessedTableName
            + " SET last_accessed = ?"
            + " WHERE projection_class = ?"
            + "  AND aggregate_id = ?";

    // for updating lastAccessed, "MERGE INTO" alternative part 2, to support more SQL flavors
    lastAccessedInsertStatement =
        " INSERT INTO "
            + lastAccessedTableName
            + " (projection_class, aggregate_id, last_accessed)"
            + " VALUES (?, ?, ?)";

    deleteLastAccessedStatement =
        "DELETE FROM " + lastAccessedTableName + " WHERE projection_class = ? AND aggregate_id = ?";

    // e.g. Oracle stores all metadata identifier as upper case
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

      final String classKey = createKeyFor(id);
      final String aggIdOrNull = id.aggregateId() != null ? id.aggregateId().toString() : null;
      final Timestamp startOfToday = Timestamp.valueOf(LocalDate.now().atStartOfDay());

      // try update first...
      update.setTimestamp(1, startOfToday);
      update.setString(2, classKey);
      update.setString(3, aggIdOrNull);

      if (update.executeUpdate() == 0) {
        // nothing to update? ... then just insert!
        insert.setString(1, classKey);
        insert.setString(2, aggIdOrNull);
        insert.setTimestamp(3, startOfToday);
        if (insert.executeUpdate() == 0) {
          log.error("Failed to update last accessed time for snapshot {}", id);
        }
      }
    } catch (Exception e) {
      log.error("Failed to update last accessed time for snapshot {}", id, e);
    }
  }

  @Override
  public void store(@NonNull SnapshotIdentifier id, @NonNull SnapshotData snapshot) {
    try (Connection connection = dataSource.getConnection()) {
      connection.setAutoCommit(false);

      try (PreparedStatement update = connection.prepareStatement(snapshotUpdateStatement);
          PreparedStatement insert = connection.prepareStatement(snapshotInsertStatement)) {

        final String lastFactId = snapshot.lastFactId().toString();
        final byte[] bytes = snapshot.serializedProjection();
        final String snapshotSerializerId = snapshot.snapshotSerializerId().name();
        final String classKey = createKeyFor(id);
        final String aggIdOrNull = id.aggregateId() != null ? id.aggregateId().toString() : null;

        // try update first...
        update.setString(1, lastFactId);
        update.setBytes(2, bytes);
        update.setString(3, snapshotSerializerId);
        update.setString(4, classKey);
        update.setString(5, aggIdOrNull);
        final double updated = update.executeUpdate();

        if (updated == 0) {
          // nothing to update? ... then just insert!
          insert.setString(1, classKey);
          insert.setString(2, aggIdOrNull);
          insert.setString(3, lastFactId);
          insert.setBytes(4, bytes);
          insert.setString(5, snapshotSerializerId);
          final int inserted = insert.executeUpdate();

          if (inserted == 0) {
            throw new IllegalStateException(
                "Failed to insert snapshot into database. SnapshotId: " + id);
          }
        }
        updateLastAccessedTime(id);
        connection.commit();
      } catch (Exception e) {
        try {
          connection.rollback();
        } catch (SQLException sqlException) {
          e.addSuppressed(sqlException);
        }
        throw e;
      } finally {
        try {
          connection.setAutoCommit(true);
        } catch (SQLException ignored) {
          // what can you do...?
        }
      }
    } catch (SQLException e) {
      throw new IllegalStateException(
          "Failed to insert snapshot into database. SnapshotId: " + id, e);
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

  @VisibleForTesting
  UnaryOperator<String> resolveIdentifierNormalizer() {
    try (Connection connection = dataSource.getConnection()) {
      if (connection.getMetaData().storesUpperCaseIdentifiers()) return String::toUpperCase;
      if (connection.getMetaData().storesLowerCaseIdentifiers()) return String::toLowerCase;
      log.debug(
          "Metadata identifiers stored neither in lower nor UPPER case, trying without any modifying identity normalizer");
      return UnaryOperator.identity();
    } catch (SQLException e) {
      log.warn(
          "Unable to determine whether this database stores metadata identifiers as lower/upper/mixed case, trying without any modifying identity normalizer",
          e);
      return UnaryOperator.identity();
    }
  }
}
