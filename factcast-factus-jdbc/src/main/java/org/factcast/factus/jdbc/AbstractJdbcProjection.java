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
import java.time.Duration;
import javax.sql.DataSource;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Delegate;
import org.factcast.factus.jdbc.JdbcFactStreamPosition.ProjectionType;
import org.factcast.factus.projection.FactStreamPositionAware;
import org.factcast.factus.projection.WriterToken;

/**
 * Projection that gets both its write token and its fact stream position from plain JDBC, so that
 * no additional infrastructure is needed. See {@link JdbcWriterTokenManager} and {@link
 * JdbcFactStreamPosition} for the tables involved.
 *
 * <p>The position is written on its own connection, so it is <em>not</em> atomic with the
 * projection's own updates. Where that matters, use the Spring-transactional variants from
 * factcast-factus-spring-tx.
 */
public abstract class AbstractJdbcProjection implements JdbcProjection {

  @Getter @NonNull private final DataSource dataSource;
  @Getter @NonNull protected final String projectionKey;
  @NonNull protected final JdbcWriterTokenManager writerTokenManager;

  @Delegate(types = FactStreamPositionAware.class)
  @NonNull
  private final JdbcFactStreamPosition factStreamPosition;

  protected AbstractJdbcProjection(@NonNull DataSource dataSource, @NonNull ProjectionType type) {
    this(dataSource, JdbcWriterTokenManager.DEFAULT_LOCK_TABLE_NAME, type.tableName());
  }

  protected AbstractJdbcProjection(
      @NonNull DataSource dataSource,
      @NonNull String lockTableName,
      @NonNull String positionTableName) {
    this.dataSource = dataSource;
    this.projectionKey = getScopedName().asString();
    this.writerTokenManager =
        JdbcWriterTokenManager.create(
            dataSource,
            projectionKey,
            lockTableName,
            JdbcWriterTokenManager.DEFAULT_LOCK_AT_MOST_FOR,
            JdbcWriterTokenManager.DEFAULT_LOCK_AT_LEAST_FOR);
    this.factStreamPosition =
        new JdbcFactStreamPosition(dataSource, positionTableName, projectionKey);
  }

  protected AbstractJdbcProjection(
      @NonNull DataSource dataSource,
      @NonNull JdbcWriterTokenManager writerTokenManager,
      @NonNull JdbcFactStreamPosition factStreamPosition) {
    this.dataSource = dataSource;
    this.projectionKey = getScopedName().asString();
    this.writerTokenManager = writerTokenManager;
    this.factStreamPosition = factStreamPosition;
  }

  @Nullable
  @Override
  public WriterToken acquireWriteToken(@NonNull Duration maxWait) {
    return writerTokenManager.acquireWriteToken(maxWait);
  }
}
