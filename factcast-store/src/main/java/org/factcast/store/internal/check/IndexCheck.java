package org.factcast.store.internal.check;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.factcast.store.internal.PgConstants;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class IndexCheck {
  private final JdbcTemplate jdbc;

  @Scheduled(cron = "${factcast.store.indexCheckCron:0 0 3 * * *}")
  public void checkIndexes() {
    log.debug("checking indexes");
    List<String> invalid = jdbc.queryForList(PgConstants.BROKEN_INDEX_NAMES, String.class);
    log.debug("found {} invalid index(es)", invalid.size() > 0 ? invalid.size() : "no");
    invalid.forEach(
        s -> {
          log.warn("Detected invalid index: {}", s);
        });
  }
}
