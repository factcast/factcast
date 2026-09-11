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
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import lombok.NonNull;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;
import org.factcast.factus.projection.FactStreamPositionAware;
import org.factcast.factus.projection.WriterToken;
import org.factcast.factus.spring.tx.AbstractSpringTxSubscribedProjection;
import org.factcast.factus.spring.tx.jdbc.JdbcFactStreamPosition.ProjectionType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Subscribed projection that gets both its write token and its fact stream position from plain
 * JDBC, so that no additional infrastructure is needed. See {@link JdbcWriterTokenManager} and
 * {@link JdbcFactStreamPosition} for the tables involved.
 */
@Slf4j
public abstract class AbstractSpringJdbcSubscribedProjection
    extends AbstractSpringTxSubscribedProjection {

  private final JdbcWriterTokenManager writerTokenManager;
  private final AtomicReference<WriterToken> writerToken = new AtomicReference<>();

  @Delegate(types = FactStreamPositionAware.class)
  private final JdbcFactStreamPosition factStreamPosition;

  protected AbstractSpringJdbcSubscribedProjection(
      @NonNull PlatformTransactionManager platformTransactionManager,
      @NonNull JdbcTemplate jdbcTemplate) {
    this(
        platformTransactionManager,
        jdbcTemplate,
        JdbcWriterTokenManager.DEFAULT_LOCK_TABLE_NAME,
        ProjectionType.SUBSCRIBED.tableName());
  }

  protected AbstractSpringJdbcSubscribedProjection(
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

  protected AbstractSpringJdbcSubscribedProjection(
      @NonNull PlatformTransactionManager platformTransactionManager,
      @NonNull JdbcWriterTokenManager writerTokenManager,
      @NonNull JdbcFactStreamPosition factStreamPosition) {
    super(platformTransactionManager);
    this.writerTokenManager = writerTokenManager;
    this.factStreamPosition = factStreamPosition;
  }

  @Nullable
  @Override
  public WriterToken acquireWriteToken(@NonNull Duration maxWait) {
    WriterToken token = writerTokenManager.acquireWriteToken(maxWait);
    if (token != null) {
      // a displaced token shares its lease owner with the new one, so its keepalive would keep
      // renewing the new one's lease and report the displaced token valid again
      closeQuietly(writerToken.getAndSet(token));
    }
    return token;
  }

  private void closeQuietly(@Nullable WriterToken displaced) {
    if (displaced == null) {
      return;
    }
    try {
      displaced.close();
    } catch (Exception e) {
      log.warn("Failed to close the writer token replaced by a new one", e);
    }
  }

  /**
   * Whether this instance currently holds the write lock. Use it to gate work that is triggered
   * from outside the fact stream, like a cleanup schedule.
   */
  protected boolean hasLock() {
    WriterToken token = writerToken.get();
    return token != null && token.isValid();
  }
}
