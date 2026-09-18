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
package org.factcast.store.internal.horizon;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;
import lombok.NonNull;
import org.factcast.core.subscription.observer.HighWaterMark;
import org.factcast.store.internal.PgConstants;
import org.factcast.store.internal.PgMetrics;
import org.factcast.store.internal.StoreMetrics;
import org.factcast.store.internal.lock.FactTableWriteLock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Advances the persisted horizon behind the publication advisory-lock barrier.
 *
 * <p>Regular publishers hold the PUBLISH lock shared for their entire transaction. Taking it
 * exclusively therefore waits for all earlier publishers and prevents a later publisher from
 * consuming a serial until both maxima have been read and persisted.
 */
public class PgFactStreamHorizonProvider extends ReadOnlyPgFactStreamHorizonProvider {

  static final String HIGHWATER_NOTIFICATION = "SELECT COALESCE(MAX(ser),0) FROM notification";
  static final String UPDATE_HORIZON =
      "UPDATE factstream_horizon "
          + "SET fact_ser=?, fact_id=?, notification_ser=GREATEST(notification_ser, ?) "
          + "WHERE id = 1 RETURNING fact_ser, fact_id, notification_ser";

  private final @NonNull JdbcTemplate jdbcTemplate;
  private final @NonNull FactTableWriteLock factTableWriteLock;
  private final @NonNull PgMetrics metrics;
  private final @NonNull TransactionTemplate transactionTemplate;

  public PgFactStreamHorizonProvider(
      @NonNull DataSource primaryDataSource,
      @NonNull JdbcTemplate jdbcTemplate,
      @NonNull FactTableWriteLock factTableWriteLock,
      @NonNull PgMetrics metrics,
      @NonNull PlatformTransactionManager transactionManager) {
    super(primaryDataSource);
    this.jdbcTemplate = jdbcTemplate;
    this.factTableWriteLock = factTableWriteLock;
    this.metrics = metrics;
    transactionTemplate = new TransactionTemplate(transactionManager);
    transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  @Override
  public synchronized @NonNull FactStreamHorizon advance() {
    FactStreamHorizon horizon =
        metrics.time(
            StoreMetrics.OP.ADVANCE_FACT_STREAM_HORIZON,
            () ->
                Objects.requireNonNull(
                    transactionTemplate.execute(
                        ignored -> {
                          factTableWriteLock.acquireExclusiveTXLock();
                          FactStreamHorizon next = liveHorizon();
                          HighWaterMark highWaterMark = next.highWaterMark();
                          List<FactStreamHorizon> persisted =
                              jdbcTemplate.query(
                                  UPDATE_HORIZON,
                                  (rs, rowNum) ->
                                      new FactStreamHorizon(
                                          HighWaterMark.of(
                                              rs.getObject("fact_id", UUID.class),
                                              rs.getLong("fact_ser")),
                                          rs.getLong("notification_ser")),
                                  highWaterMark.targetSer(),
                                  highWaterMark.targetId(),
                                  next.notificationSerial());
                          if (persisted.isEmpty()) {
                            throw new IllegalStateException(MISSING_HORIZON);
                          }
                          return persisted.get(0);
                        })));

    // TransactionTemplate returns only after the transaction committed successfully.
    updateCurrent(horizon);
    return horizon;
  }

  private @NonNull FactStreamHorizon liveHorizon() {
    List<HighWaterMark> highWaterMarks =
        jdbcTemplate.query(
            PgConstants.HIGHWATER_MARK,
            (rs, rowNum) ->
                HighWaterMark.of(rs.getObject("targetId", UUID.class), rs.getLong("targetSer")));
    HighWaterMark highWaterMark =
        highWaterMarks.isEmpty() ? HighWaterMark.empty() : highWaterMarks.get(0);
    Long notificationSerial = jdbcTemplate.queryForObject(HIGHWATER_NOTIFICATION, Long.class);
    return new FactStreamHorizon(
        highWaterMark, notificationSerial == null ? 0 : notificationSerial);
  }
}
