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
package org.factcast.factus.spring.tx.jdbc;

import jakarta.annotation.Nullable;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.factcast.core.FactStreamPosition;
import org.factcast.factus.projection.FactStreamPositionAware;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

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
 */
public class JdbcFactStreamPosition implements FactStreamPositionAware {

  private static final RowMapper<FactStreamPosition> ROW_MAPPER =
      (rs, rowNum) ->
          FactStreamPosition.of(rs.getObject("state", UUID.class), rs.getLong("serial"));

  private final JdbcTemplate jdbcTemplate;
  private final String tableName;
  private final String projectionName;

  public JdbcFactStreamPosition(
      @NonNull JdbcTemplate jdbcTemplate,
      @NonNull ProjectionType type,
      @NonNull String projectionKey) {
    this(jdbcTemplate, type.tableName(), projectionKey);
  }

  public JdbcFactStreamPosition(
      @NonNull JdbcTemplate jdbcTemplate,
      @NonNull String tableName,
      @NonNull String projectionKey) {
    this.jdbcTemplate = jdbcTemplate;
    this.tableName = tableName;
    this.projectionName = ProjectionNames.positionName(projectionKey);
  }

  @Nullable
  @Override
  public FactStreamPosition factStreamPosition() {
    List<FactStreamPosition> result =
        jdbcTemplate.query(
            "SELECT state, serial FROM %s WHERE name = ?".formatted(tableName),
            ROW_MAPPER,
            projectionName);
    return result.isEmpty() ? null : result.get(0);
  }

  @Override
  public void factStreamPosition(@NonNull FactStreamPosition factStreamPosition) {
    int updated =
        jdbcTemplate.update(
            "UPDATE %s SET state = ?, serial = ? WHERE name = ?".formatted(tableName),
            factStreamPosition.factId(),
            factStreamPosition.serial(),
            projectionName);
    if (updated == 0) {
      jdbcTemplate.update(
          "INSERT INTO %s (name, state, serial) VALUES (?, ?, ?)".formatted(tableName),
          projectionName,
          factStreamPosition.factId(),
          factStreamPosition.serial());
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
