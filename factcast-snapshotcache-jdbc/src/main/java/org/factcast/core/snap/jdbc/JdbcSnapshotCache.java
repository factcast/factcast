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

import com.google.common.collect.Sets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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
import org.factcast.core.snap.Snapshot;
import org.factcast.core.snap.SnapshotId;
import org.factcast.factus.snapshot.SnapshotCache;

@Slf4j
public class JdbcSnapshotCache implements SnapshotCache {
  public final String queryStatement;
  public final String mergeStatement;
  public final String deleteStatement;
  private final DataSource dataSource;

  public JdbcSnapshotCache(JdbcSnapshotProperties properties, DataSource dataSource) {
    this.dataSource = dataSource;

    String tableName = properties.getSnapshotTableName();

    queryStatement = "SELECT * FROM " + tableName + " WHERE key = ? AND uuid = ?";

    mergeStatement =
        "MERGE INTO "
            + tableName
            + " USING (VALUES (?, ?, ?, ?, ?, ?)) as new (_key, _uuid, _last_fact_id, _bytes, _compressed, _last_accessed)"
            + " ON key=_key AND uuid=_uuid"
            + " WHEN MATCHED THEN"
            + " UPDATE SET last_fact_id=_last_fact_id, bytes=_bytes, compressed=_compressed, last_accessed=_last_accessed"
            + " WHEN NOT MATCHED THEN"
            + " INSERT VALUES (_key, _uuid, _last_fact_id, _bytes, _compressed, _last_accessed)";

    deleteStatement = "DELETE FROM " + tableName + " WHERE key = ? AND uuid = ?";

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
          Sets.newHashSet("key", "uuid", "last_fact_id", "bytes", "compressed", "last_accessed");
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
  public @NonNull Optional<Snapshot> getSnapshot(@NonNull SnapshotId id) {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(queryStatement)) {
      statement.setString(1, id.key());
      statement.setString(2, id.uuid().toString());
      try (ResultSet resultSet = statement.executeQuery()) {
        if (resultSet.next()) {
          SnapshotId snapshotId =
              SnapshotId.of(resultSet.getString(1), UUID.fromString(resultSet.getString(2)));
          Snapshot snapshot =
              new Snapshot(
                  snapshotId,
                  UUID.fromString(resultSet.getString(3)),
                  resultSet.getBytes(4),
                  resultSet.getBoolean(5));

          // update last accessed
          setSnapshot(snapshot);
          return Optional.of(snapshot);
        }
      }
    }

    return Optional.empty();
  }

  @Override
  @SneakyThrows
  public void setSnapshot(@NonNull Snapshot snapshot) {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(mergeStatement)) {
      statement.setString(1, snapshot.id().key());
      statement.setString(2, snapshot.id().uuid().toString());
      statement.setString(3, snapshot.lastFact().toString());
      statement.setBytes(4, snapshot.bytes());
      statement.setBoolean(5, snapshot.compressed());
      statement.setString(6, LocalDate.now().toString());
      if (statement.executeUpdate() == 0) {
        throw new IllegalStateException(
            "Failed to insert snapshot into database. SnapshotId: " + snapshot.id());
      }
    }
  }

  @Override
  @SneakyThrows
  public void clearSnapshot(@NonNull SnapshotId id) {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(deleteStatement)) {
      statement.setString(1, id.key());
      statement.setString(2, id.uuid().toString());
      statement.executeUpdate();
    }
  }
}
