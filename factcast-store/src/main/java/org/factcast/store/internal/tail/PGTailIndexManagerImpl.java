/*
 * Copyright © 2017-2022 factcast.org
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
package org.factcast.store.internal.tail;

import static org.factcast.store.internal.PgConstants.*;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import java.time.Duration;
import java.util.*;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.factcast.store.StoreConfigurationProperties;
import org.factcast.store.internal.PgConstants;
import org.factcast.store.internal.PgMetrics;
import org.factcast.store.internal.StoreMetrics;
import org.factcast.store.internal.listen.PgConnectionSupplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.scheduling.annotation.Scheduled;

@Slf4j
@RequiredArgsConstructor
public class PGTailIndexManagerImpl implements PGTailIndexManager {
  private final PgConnectionSupplier pgConnectionSupplier;
  private final StoreConfigurationProperties props;
  private final PgMetrics pgMetrics;

  @Override
  @Scheduled(cron = "${factcast.store.tailManagementCron:0 0 0 * * *}")
  // Here we only need to ensure not two tasks are running in parallel until index
  // creation was triggered. Lock is automatically refreshed every 2,5minutes
  @SchedulerLock(name = "triggerTailCreation", lockAtMostFor = "5m")
  @SneakyThrows
  public void triggerTailCreation() {

    if (!props.isTailIndexingEnabled()) {
      return;
    }

    try (var jdbc = buildTemplate()) {
      log.info("Triggering tail index maintenance");

      var maintenancePossible = !isAnyIndexOperationInProgress(jdbc);
      if (maintenancePossible) {
        // create if necessary
        if (timeToCreateANewTail(jdbc)) {
          createNewTail(jdbc);
        }

        // delete old/invalid ones if necessary
        processExistingIndices(jdbc);
      }

      // write metrics for invalid/valid indices
      reportMetrics(jdbc, maintenancePossible);

      log.info("Done with tail index maintenance. Result: {}", getResultText(maintenancePossible));
    }
  }

  @SuppressWarnings("ConstantConditions")
  @VisibleForTesting
  void createNewTail(@NonNull JdbcTemplate jdbc) {
    long serial = jdbc.queryForObject(PgConstants.LAST_SERIAL_IN_LOG, Long.class);
    var currentTimeMillis = System.currentTimeMillis();
    var indexName = PgConstants.tailIndexName(currentTimeMillis);

    log.debug("Creating tail index {}.", indexName);

    // make sure index creation does not hang forever.
    // make 5 seconds shorter to compensate for the gap between currentTimeMillis
    // and create index
    jdbc.execute(
        PgConstants.setStatementTimeout(props.getTailCreationTimeout().minusSeconds(5).toMillis()));

    try {
      jdbc.update(PgConstants.createTailIndex(indexName, serial, props));
    } catch (RuntimeException e) {
      // keep log message in sync with asserts in
      // PGTailIndexManagerImplIntTest.doesNotCreateIndexConcurrently
      log.error("Error creating tail index {}, trying to drop it...", indexName, e);

      removeTailIndex(jdbc, indexName);
    }
  }

  @VisibleForTesting
  protected void reportMetrics(@NonNull JdbcTemplate jdbc, boolean maintenancePossible) {
    var indexesOrderedByTimeWithValidityFlag = getTailIndices(jdbc);
    var validIndexes = getValidIndices(indexesOrderedByTimeWithValidityFlag);
    var invalidIndexes = getInvalidIndices(indexesOrderedByTimeWithValidityFlag);

    var maintenance = Tag.of(MAINTENANCE, maintenancePossible ? EXECUTED : SKIPPED);
    pgMetrics
        .distributionSummary(
            StoreMetrics.VALUE.TAIL_INDICES, Tags.of(Tag.of(STATE, STATE_VALID), maintenance))
        .record(validIndexes.size());
    pgMetrics
        .distributionSummary(
            StoreMetrics.VALUE.TAIL_INDICES, Tags.of(Tag.of(STATE, STATE_INVALID), maintenance))
        .record(invalidIndexes.size());
  }

  private void processExistingIndices(@NonNull JdbcTemplate jdbc) {
    var indexesOrderedByTimeWithValidityFlag = getTailIndices(jdbc);
    removeOldestValidIndices(jdbc, indexesOrderedByTimeWithValidityFlag);
    removeInvalidIndices(jdbc, indexesOrderedByTimeWithValidityFlag);
  }

  @NonNull
  private List<String> getValidIndices(List<Map<String, Object>> indexesWithValidityFlag) {
    return new ArrayList<>(
        indexesWithValidityFlag.stream()
            .filter(r -> r.get(VALID_COLUMN).equals(IS_VALID))
            .map(r -> r.get(INDEX_NAME_COLUMN).toString())
            .toList());
  }

  private @NonNull List<String> getInvalidIndices(
      List<Map<String, Object>> indexesWithValidityFlag) {
    return indexesWithValidityFlag.stream()
        .filter(r -> r.get(VALID_COLUMN).equals(IS_INVALID))
        .map(r -> r.get(INDEX_NAME_COLUMN).toString())
        .toList();
  }

  @VisibleForTesting
  boolean timeToCreateANewTail(@NonNull JdbcTemplate jdbc) {
    var indexesOrderedByTimeWithValidityFlag = getTailIndices(jdbc);
    var indices = getValidIndices(indexesOrderedByTimeWithValidityFlag);

    if (indices.isEmpty()) {
      return true;
    }

    var newestIndex = indices.get(0);

    return exceedsMinimumTailAge(newestIndex);
  }

  @VisibleForTesting
  protected boolean isAnyIndexOperationInProgress(@NonNull JdbcTemplate jdbc) {
    final var operations = jdbc.queryForList(INDEX_OPERATIONS_IN_PROGRESS);

    if (operations.isEmpty()) {
      return false;
    }

    log.debug("Index operations in progress: {}", operations);

    return true;
  }

  @VisibleForTesting
  protected void removeOldestValidIndices(
      @NonNull JdbcTemplate jdbc, @NonNull List<Map<String, Object>> indexesWithValidityFlag) {
    var validIndexes = getValidIndices(indexesWithValidityFlag);

    while (validIndexes.size() > props.getTailGenerationsToKeep()) {
      // Oldest is last in list as order is descending by name.
      removeTailIndex(jdbc, validIndexes.remove(validIndexes.size() - 1));
    }
  }

  private static @NonNull List<Map<String, Object>> getTailIndices(JdbcTemplate jdbc) {
    return jdbc.queryForList(LIST_FACT_INDEXES_WITH_VALIDATION);
  }

  private void removeInvalidIndices(
      JdbcTemplate jdbc, List<Map<String, Object>> indexesWithValidityFlag) {
    getInvalidIndices(indexesWithValidityFlag).forEach(i -> removeTailIndex(jdbc, i));
  }

  @VisibleForTesting
  void removeTailIndex(@NonNull JdbcTemplate jdbc, @NonNull String indexName) {
    Preconditions.checkArgument(indexName.startsWith(TAIL_INDEX_NAME_PREFIX), "Invalid index name");

    try {
      log.debug("Dropping tail index {}", indexName);
      jdbc.execute(PgConstants.setStatementTimeout(Duration.ofHours(1).toMillis()));
      jdbc.update(PgConstants.dropTailIndex(indexName));
    } catch (RuntimeException e) {
      log.error("Error dropping tail index {}.", indexName, e);
    }
  }

  /**
   * @param index name of the index
   * @return true, if the given index exceeds the configured minimum tail age and a new index should
   *     be created. false otherwise.
   */
  private boolean exceedsMinimumTailAge(@NonNull String index) {
    long indexTimestamp =
        Long.parseLong(index.substring(PgConstants.TAIL_INDEX_NAME_PREFIX.length()));
    Duration age = Duration.ofMillis(System.currentTimeMillis() - indexTimestamp);
    Duration minAge = props.getMinimumTailAge();
    return minAge.minus(age).isNegative();
  }

  private static @NonNull String getResultText(boolean maintenancePossible) {
    return maintenancePossible ? EXECUTED : SKIPPED + " because of ongoing index operations";
  }

  @VisibleForTesting
  @SneakyThrows
  protected ClosingJdbcTemplate buildTemplate() {
    var singleConnectionDataSource =
        new SingleConnectionDataSource(
            pgConnectionSupplier.getUnpooledConnection("tail-index-maintenance"), true);
    return new ClosingJdbcTemplate(singleConnectionDataSource);
  }

  @VisibleForTesting
  protected static class ClosingJdbcTemplate extends JdbcTemplate implements AutoCloseable {
    private final SingleConnectionDataSource singleConnectionDataSource;

    public ClosingJdbcTemplate(SingleConnectionDataSource dataSource) {
      super(dataSource);
      this.singleConnectionDataSource = dataSource;
    }

    @Override
    public void close() {
      singleConnectionDataSource.destroy();
    }
  }

  private static final String STATE = "state";
  private static final String STATE_VALID = "valid";
  private static final String STATE_INVALID = "invalid";
  private static final String MAINTENANCE = "maintenance";
  private static final String EXECUTED = "executed";
  private static final String SKIPPED = "skipped";
}
