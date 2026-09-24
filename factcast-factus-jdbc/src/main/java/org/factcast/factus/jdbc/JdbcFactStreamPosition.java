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
package org.factcast.factus.jdbc;

import jakarta.annotation.Nullable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.factcast.core.FactStreamPosition;
import org.factcast.factus.projection.FactStreamPositionAware;

/**
 * Keeps the fact stream position of a projection in a table of the shape
 *
 * <pre>
 * CREATE TABLE managed_projection (
 *     name   varchar(255),
 *     state  UUID,
 *     serial bigint DEFAULT -1,
 *     PRIMARY KEY (name)
 * );
 * </pre>
 *
 * with an identically shaped {@code subscribed_projection}.
 *
 * <p>Whether a position update is atomic with the projection's own updates is decided by the {@link
 * DataSource} passed in: one that hands out the connection of an ongoing transaction makes it so, a
 * plain one does not.
 */
public class JdbcFactStreamPosition implements FactStreamPositionAware {

  private static final Pattern UNQUOTED_IDENTIFIER =
      Pattern.compile("[A-Za-z_][A-Za-z0-9_$]{0,62}");

  private final DataSource dataSource;
  private final String tableName;
  private final String projectionName;

  public JdbcFactStreamPosition(
      @NonNull DataSource dataSource, @NonNull ProjectionType type, @NonNull String projectionKey) {
    this(dataSource, type.tableName(), projectionKey);
  }

  public JdbcFactStreamPosition(
      @NonNull DataSource dataSource, @NonNull String tableName, @NonNull String projectionKey) {
    this.dataSource = dataSource;
    this.tableName = validTableName(tableName);
    this.projectionName = ProjectionNames.positionName(projectionKey);
  }

  private static String validTableName(String tableName) {
    if (!UNQUOTED_IDENTIFIER.matcher(tableName).matches()) {
      throw new IllegalArgumentException("'%s' is not a valid table name".formatted(tableName));
    }
    return tableName;
  }

  @Nullable
  @Override
  @SuppressWarnings("java:S2077") // tableName is validated in the constructor
  public FactStreamPosition factStreamPosition() {
    String sql = "SELECT state, serial FROM %s WHERE name = ?".formatted(tableName);
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, projectionName);
      try (ResultSet resultSet = statement.executeQuery()) {
        if (!resultSet.next()) {
          return null;
        }
        return FactStreamPosition.of(
            resultSet.getObject("state", UUID.class), resultSet.getLong("serial"));
      }
    } catch (SQLException e) {
      throw new IllegalStateException(
          "Cannot read the fact stream position of '%s' from %s"
              .formatted(projectionName, tableName),
          e);
    }
  }

  @Override
  public void factStreamPosition(@NonNull FactStreamPosition factStreamPosition) {
    try (Connection connection = dataSource.getConnection()) {
      if (update(connection, factStreamPosition) == 0) {
        insert(connection, factStreamPosition);
      }
    } catch (SQLException e) {
      throw new IllegalStateException(
          "Cannot write the fact stream position of '%s' to %s"
              .formatted(projectionName, tableName),
          e);
    }
  }

  @SuppressWarnings("java:S2077") // tableName is validated in the constructor
  private int update(Connection connection, FactStreamPosition position) throws SQLException {
    String sql = "UPDATE %s SET state = ?, serial = ? WHERE name = ?".formatted(tableName);
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setObject(1, position.factId());
      statement.setLong(2, position.serial());
      statement.setString(3, projectionName);
      return statement.executeUpdate();
    }
  }

  @SuppressWarnings("java:S2077") // tableName is validated in the constructor
  private void insert(Connection connection, FactStreamPosition position) throws SQLException {
    String sql = "INSERT INTO %s (name, state, serial) VALUES (?, ?, ?)".formatted(tableName);
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, projectionName);
      statement.setObject(2, position.factId());
      statement.setLong(3, position.serial());
      statement.executeUpdate();
    }
  }

  @RequiredArgsConstructor
  @Getter
  public enum ProjectionType {
    MANAGED("managed_projection"),
    SUBSCRIBED("subscribed_projection");

    private final String tableName;
  }
}
