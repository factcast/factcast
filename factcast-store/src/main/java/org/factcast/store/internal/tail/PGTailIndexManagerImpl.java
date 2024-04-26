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

import static java.util.function.Predicate.*;
import static org.factcast.store.internal.PgConstants.*;

import com.google.common.annotations.VisibleForTesting;
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

  private static final String STATE = "state";
  private static final String STATE_VALID = "valid";
  private static final String STATE_INVALID = "invalid";

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
      log.debug("Triggering tail index maintenance");

      var indexesOrderedByTimeWithValidityFlag = getTailIndices(jdbc);
      var validIndexes = getValidIndices(indexesOrderedByTimeWithValidityFlag);

      // create if necessary
      if (timeToCreateANewTail(validIndexes)
          && !indexCreationInProgress(indexesOrderedByTimeWithValidityFlag)) {
        createNewTail(jdbc);
      }

      // delete old/invalid ones if necessary
      processExistingIndices(jdbc);

      reportMetrics(jdbc);
    }

    log.debug("Done with tail index maintenance");
  }

  @SuppressWarnings("ConstantConditions")
  @VisibleForTesting
  void createNewTail(JdbcTemplate jdbc) {
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
      jdbc.update(PgConstants.createTailIndex(indexName, serial));

    } catch (RuntimeException e) {
      // keep log message in sync with asserts in
      // PGTailIndexManagerImplIntTest.doesNotCreateIndexConcurrently
      log.error("Error creating tail index {}, trying to drop it...", indexName, e);

      removeIndex(jdbc, indexName);
    }
  }

  private void reportMetrics(JdbcTemplate jdbc) {
    var indexesOrderedByTimeWithValidityFlag = getTailIndices(jdbc);
    var validIndexes = getValidIndices(indexesOrderedByTimeWithValidityFlag);
    var invalidIndexes = getInvalidIndices(indexesOrderedByTimeWithValidityFlag);

    pgMetrics
        .distributionSummary(StoreMetrics.VALUE.TAIL_INDICES, Tags.of(Tag.of(STATE, STATE_VALID)))
        .record(validIndexes.size());

    pgMetrics
        .distributionSummary(StoreMetrics.VALUE.TAIL_INDICES, Tags.of(Tag.of(STATE, STATE_INVALID)))
        .record(invalidIndexes.size());
  }

  @VisibleForTesting
  @SneakyThrows
  protected CloseableJdbcTemplate buildTemplate() {
    var singleConnectionDataSource =
        new SingleConnectionDataSource(pgConnectionSupplier.get(), true);
    return new CloseableJdbcTemplate(singleConnectionDataSource);
  }

  private void processExistingIndices(JdbcTemplate jdbc) {
    var indexesOrderedByTimeWithValidityFlag = getTailIndices(jdbc);
    var validIndexes = getValidIndices(indexesOrderedByTimeWithValidityFlag);
    removeOldestValidIndices(jdbc, validIndexes);
    removeNonRecentInvalidIndices(jdbc, indexesOrderedByTimeWithValidityFlag);
  }

  @NonNull
  private List<String> getValidIndices(List<Map<String, Object>> indexesWithValidityFlag) {
    return new ArrayList<>(
        indexesWithValidityFlag.stream()
            .filter(r -> r.get(VALID_COLUMN).equals(IS_VALID))
            .map(r -> r.get(INDEX_NAME_COLUMN).toString())
            .toList());
  }

  @VisibleForTesting
  boolean timeToCreateANewTail(@NonNull List<String> indexes) {
    if (indexes.isEmpty()) {
      return true;
    }

    var newestIndex = indexes.get(0);

    return exceedsMinimumTailAge(newestIndex);
  }

  private boolean indexCreationInProgress(List<Map<String, Object>> indexesWithValidityFlag) {
    return indexesWithValidityFlag.stream()
        .filter(r -> r.get(VALID_COLUMN).equals(IS_INVALID))
        .map(r -> r.get(INDEX_NAME_COLUMN).toString())
        .anyMatch(not(this::isNotRecent));
  }

  @VisibleForTesting
  protected void removeOldestValidIndices(JdbcTemplate jdbc, List<String> validIndexesOrdered) {
    while (validIndexesOrdered.size() > props.getTailGenerationsToKeep()) {
      // Oldest is last in list as order is descending by name.
      removeIndex(jdbc, validIndexesOrdered.remove(validIndexesOrdered.size() - 1));
    }
  }

  private static @NonNull List<Map<String, Object>> getTailIndices(JdbcTemplate jdbc) {
    return jdbc.queryForList(LIST_FACT_INDEXES_WITH_VALIDATION);
  }

  private void removeNonRecentInvalidIndices(
      JdbcTemplate jdbc, List<Map<String, Object>> indexesWithValidityFlag) {
    getInvalidIndices(indexesWithValidityFlag).forEach(i -> removeIndex(jdbc, i));
  }

  private @NonNull List<String> getInvalidIndices(
      List<Map<String, Object>> indexesOrderedByTimeWithValidityFlag) {
    return indexesOrderedByTimeWithValidityFlag.stream()
        .filter(r -> r.get(VALID_COLUMN).equals(IS_INVALID))
        .map(r -> r.get(INDEX_NAME_COLUMN).toString())
        .filter(this::isNotRecent)
        .toList();
  }

  @VisibleForTesting
  void removeIndex(@NonNull JdbcTemplate jdbc, @NonNull String indexName) {
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

  /**
   * If an index was not created recently.
   *
   * <p>Recently created (but invalid) indices might still turn valid, hence do not touch them.
   *
   * <p>Not recently created indices that are invalid will most likely not recover any more, hence
   * drop them.
   *
   * @param index name of the index
   * @return true, if the given index has not been created recently; false otherwise.
   */
  private boolean isNotRecent(@NonNull String index) {
    long indexTimestamp =
        Long.parseLong(index.substring(PgConstants.TAIL_INDEX_NAME_PREFIX.length()));

    Duration age = Duration.ofMillis(System.currentTimeMillis() - indexTimestamp);
    // use the time after which a hanging index creation would run into a timeout,
    // plus 5 extra seconds, as the millis are obtained before the index creation is
    // started
    Duration minAge = props.getTailCreationTimeout();

    return minAge.minus(age).isNegative();
  }

  @VisibleForTesting
  protected static class CloseableJdbcTemplate extends JdbcTemplate implements AutoCloseable {
    private final SingleConnectionDataSource singleConnectionDataSource;

    public CloseableJdbcTemplate(SingleConnectionDataSource dataSource) {
      super(dataSource);
      this.singleConnectionDataSource = dataSource;
    }

    @Override
    public void close() {
      singleConnectionDataSource.destroy();
    }
  }
}
