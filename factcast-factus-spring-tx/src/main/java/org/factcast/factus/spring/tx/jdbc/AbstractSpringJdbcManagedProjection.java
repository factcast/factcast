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

import java.time.Duration;
import lombok.NonNull;
import lombok.experimental.Delegate;
import org.factcast.factus.projection.FactStreamPositionAware;
import org.factcast.factus.projection.WriterToken;
import org.factcast.factus.spring.tx.AbstractSpringTxManagedProjection;
import org.factcast.factus.spring.tx.jdbc.JdbcFactStreamPosition.ProjectionType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Managed projection that gets both its write token and its fact stream position from plain JDBC,
 * so that no additional infrastructure is needed. See {@link JdbcWriterTokenManager} and {@link
 * JdbcFactStreamPosition} for the tables involved.
 */
public abstract class AbstractSpringJdbcManagedProjection
    extends AbstractSpringTxManagedProjection {

  private final JdbcWriterTokenManager writerTokenManager;

  @Delegate(types = FactStreamPositionAware.class)
  private final JdbcFactStreamPosition factStreamPosition;

  protected AbstractSpringJdbcManagedProjection(
      @NonNull PlatformTransactionManager platformTransactionManager,
      @NonNull JdbcTemplate jdbcTemplate) {
    this(
        platformTransactionManager,
        jdbcTemplate,
        JdbcWriterTokenManager.DEFAULT_LOCK_TABLE_NAME,
        ProjectionType.MANAGED.tableName());
  }

  protected AbstractSpringJdbcManagedProjection(
      @NonNull PlatformTransactionManager platformTransactionManager,
      @NonNull JdbcTemplate jdbcTemplate,
      @NonNull String lockTableName,
      @NonNull String positionTableName) {
    super(platformTransactionManager);
    String projectionKey = getScopedName().asString();
    this.writerTokenManager =
        JdbcWriterTokenManager.create(
            jdbcTemplate,
            projectionKey,
            lockTableName,
            JdbcWriterTokenManager.DEFAULT_LOCK_AT_MOST_FOR,
            JdbcWriterTokenManager.DEFAULT_LOCK_AT_LEAST_FOR);
    this.factStreamPosition =
        new JdbcFactStreamPosition(jdbcTemplate, positionTableName, projectionKey);
  }

  protected AbstractSpringJdbcManagedProjection(
      @NonNull PlatformTransactionManager platformTransactionManager,
      @NonNull JdbcWriterTokenManager writerTokenManager,
      @NonNull JdbcFactStreamPosition factStreamPosition) {
    super(platformTransactionManager);
    this.writerTokenManager = writerTokenManager;
    this.factStreamPosition = factStreamPosition;
  }

  @Override
  public WriterToken acquireWriteToken(@NonNull Duration maxWait) {
    return writerTokenManager.acquireWriteToken(maxWait);
  }
}
