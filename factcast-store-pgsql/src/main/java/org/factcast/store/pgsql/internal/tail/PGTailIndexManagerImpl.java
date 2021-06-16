package org.factcast.store.pgsql.internal.tail;

import com.google.common.annotations.VisibleForTesting;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.factcast.store.pgsql.PgConfigurationProperties;
import org.factcast.store.pgsql.internal.PgConstants;
import org.jetbrains.annotations.Nullable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;

@RequiredArgsConstructor
public class PGTailIndexManagerImpl implements PGTailIndexManager {

  private final JdbcTemplate jdbc;
  private final PgConfigurationProperties props;
  private HighWaterMark target = new HighWaterMark();

  @Override
  @Scheduled(cron = "* * */1 * * *")
  @SchedulerLock(name = "triggerTailCreation", lockAtMostFor = "120m")
  public void triggerTailCreation() {

    if (!props.isTailIndexingEnabled()) {
      return;
    }

    List<String> indexes = jdbc.queryForList(PgConstants.LIST_FACT_INDEXES, String.class);
    if (timeToCreateANewTail(indexes)) {
      createNewTail();
    }

    Collections.reverse(indexes);
    while (indexes.size() > props.getTailGenerationsToKeep()) {
      removeIndex(indexes.remove(0));
    }

    refreshHighwaterMark();
  }

  private void refreshHighwaterMark() {
    List<HighWaterMark> highwater =
        jdbc.queryForList(PgConstants.HIGHWATER_MARK, HighWaterMark.class);
    // needed to allow the result to be empty
    if (!highwater.isEmpty()) {
      target = highwater.get(0);
    }
  }

  @VisibleForTesting
  void removeIndex(String indexName) {
    jdbc.update(PgConstants.dropTailIndex(indexName));
  }

  @VisibleForTesting
  boolean timeToCreateANewTail(List<String> indexes) {
    if (indexes.isEmpty()) {
      return true;
    }

    long youngestIndexTimestamp =
        Long.parseLong(indexes.get(0).substring("idx_fact_tail_".length()));
    Duration age = Duration.ofMillis(System.currentTimeMillis() - youngestIndexTimestamp);
    Duration minAge = props.getMinimumTailAgeInDays();
    return minAge.minus(age).isNegative();
  }

  @SuppressWarnings("ConstantConditions")
  @VisibleForTesting
  void createNewTail() {
    long serial = jdbc.queryForObject(PgConstants.LAST_SERIAL_IN_LOG, Long.class);
    jdbc.update(PgConstants.createTailIndex(System.currentTimeMillis(), serial));
  }

  @Nullable
  @Override
  public UUID targetId() {
    return target.targetId();
  }

  @Override
  public long targetSer() {
    return target.targetSer();
  }
}

@Data
class HighWaterMark {
  private UUID targetId = null;
  private long targetSer = 0;
}
