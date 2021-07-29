package org.factcast.store.pgsql.internal.check;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.factcast.store.pgsql.internal.PgConstants;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class IndexCheck {
  private final JdbcTemplate jdbc;

  @Scheduled(cron = "${factcast.store.pgsql.indexCheckCron:0 0 3 * * *}")
  public void checkIndexes() {
    jdbc.queryForList(PgConstants.BROKEN_INDEX_NAMES, String.class)
        .forEach(
            s -> {
              log.warn("Detected broken index: {}", s);
            });
  }
}
