package org.factcast.store.internal.tail;

import static java.util.function.Predicate.*;
import static org.factcast.store.internal.PgConstants.*;

import com.google.common.annotations.VisibleForTesting;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.factcast.store.StoreConfigurationProperties;
import org.factcast.store.internal.PgConstants;
import org.jetbrains.annotations.Nullable;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;

@RequiredArgsConstructor
@Slf4j
public class PGTailIndexManagerImpl implements PGTailIndexManager {

  private final JdbcTemplate jdbc;
  private final StoreConfigurationProperties props;
  private HighWaterMark target = new HighWaterMark();

  @Nullable
  @Override
  public UUID targetId() {
    return target.targetId();
  }

  @Override
  public long targetSer() {
    return target.targetSer();
  }

  @Override
  @Scheduled(cron = "${factcast.store.tailManagementCron:0 0 0 * * *}")
  // Here we only need to ensure not two tasks are running in parallel until index creation
  // was triggered. 5 minutes should be more than enough.
  @SchedulerLock(name = "triggerTailCreation", lockAtMostFor = "5m")
  public void triggerTailCreation() {

    if (!props.isTailIndexingEnabled()) {
      return;
    }

    log.debug("Triggering tail index maintenance");

    var indexesWithValidityFlag = jdbc.queryForList(LIST_FACT_INDEXES_WITH_VALIDATION);
    var validIndexes = getValidIndices(indexesWithValidityFlag);
    // delete first
    removeOldestValidIndices(validIndexes);
    removeNonRecentInvalidIndices(indexesWithValidityFlag);

    // THEN create
    if (timeToCreateANewTail(validIndexes) && !indexCreationInProgress(indexesWithValidityFlag)) {
      createNewTail();
    }

    refreshHighwaterMark();

    log.debug("Done with tail index maintenance");
  }

  @NonNull
  private List<String> getValidIndices(List<Map<String, Object>> indexesWithValidityFlag) {
    return indexesWithValidityFlag.stream()
        .filter(r -> r.get(VALID_COLUMN).equals(IS_VALID))
        .map(r -> r.get(INDEX_NAME_COLUMN).toString())
        .collect(Collectors.toList());
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

  @SuppressWarnings("ConstantConditions")
  @VisibleForTesting
  void createNewTail() {
    long serial = jdbc.queryForObject(PgConstants.LAST_SERIAL_IN_LOG, Long.class);
    var currentTimeMillis = System.currentTimeMillis();
    var indexName = PgConstants.tailIndexName(currentTimeMillis);

    log.debug("Creating tail index {}.", indexName);

    // make sure index creation does not hang forever.
    // make 5 seconds shorter to compensate for the gap between currentTimeMillis and create index
    jdbc.execute(
        PgConstants.setStatementTimeout(props.getTailCreationTimeout().minusSeconds(5).toMillis()));

    try {
      jdbc.update(PgConstants.createTailIndex(indexName, serial));

    } catch (RuntimeException e) {
      // keep log message in sync with asserts in
      // PGTailIndexManagerImplIntTest.doesNotCreateIndexConcurrently
      log.error("Error creating tail index {}, trying to drop it...", indexName, e);

      try {
        jdbc.update(PgConstants.dropTailIndex(indexName));
        log.debug(
            "Successfully dropped tail index {} after running into an error during creation.",
            indexName);

      } catch (RuntimeException e2) {
        // keep log message in sync with asserts in
        // PGTailIndexManagerImplIntTest.doesNotCreateIndexConcurrently
        log.error(
            "After error, tried to drop the index that could not be created ({}), but received another error:",
            indexName,
            e2);
      }
    }
  }

  private void removeOldestValidIndices(List<String> validIndexes) {
    Collections.reverse(validIndexes);
    while (validIndexes.size() > props.getTailGenerationsToKeep()) {
      removeIndex(validIndexes.remove(0));
    }
  }

  private void removeNonRecentInvalidIndices(List<Map<String, Object>> indexesWithValidityFlag) {
    indexesWithValidityFlag.stream()
        .filter(r -> r.get(VALID_COLUMN).equals(IS_INVALID))
        .map(r -> r.get(INDEX_NAME_COLUMN).toString())
        .filter(this::isNotRecent)
        .forEach(this::removeIndex);
  }

  @VisibleForTesting
  void removeIndex(@NonNull String indexName) {
    jdbc.update(PgConstants.dropTailIndex(indexName));
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
    // plus 5 extra seconds, as the millis are obtained before the index creation is started
    Duration minAge = props.getTailCreationTimeout();

    return minAge.minus(age).isNegative();
  }

  @VisibleForTesting
  void refreshHighwaterMark() {
    try {
      target =
          jdbc.queryForObject(
              PgConstants.HIGHWATER_MARK,
              (rs, rowNum) -> {
                HighWaterMark ret = new HighWaterMark();
                ret.targetId(rs.getObject("targetId", UUID.class));
                ret.targetSer(rs.getLong("targetSer"));
                return ret;
              });
    } catch (EmptyResultDataAccessException noFactsAtAll) {
      // ignore
    }
  }

  @Data
  static class HighWaterMark {
    private UUID targetId = null;
    private long targetSer = 0;
  }
}
