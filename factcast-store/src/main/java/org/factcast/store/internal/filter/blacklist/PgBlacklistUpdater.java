package org.factcast.store.internal.filter.blacklist;

import com.google.common.annotations.VisibleForTesting;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.factcast.store.internal.filter.PgBlacklist;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
public class PgBlacklistUpdater {
  private BlacklistFetcher fetcher;

  private final PgBlacklist blacklist;
  private PgUpdater pgUpdater;

  public PgBlacklistUpdater(PgBlacklist blacklist){
    this.blacklist = blacklist;
  }
    public void updatePgBlacklist(){
      Map<UUID, String> newBlacklist = fetcher.fetchBlacklist();


      // compare and for new entries
      List<String> newBlacklistEntries = new ArrayList<String>();
      newBlacklist.forEach((key, reason)-> {
        if (!blacklist.isBlocked(key)){
          newBlacklistEntries.add("(" + key + ", " + reason + ")");
        }
      });

      if (!newBlacklistEntries.isEmpty()){
        pgUpdater.insert(buildQuery(newBlacklistEntries));
      }
    }

  private String buildQuery(List<String> items){
    return "INSERT INTO blacklist(id, reason) VALUES " +
        String.join(", ", items)
        + "ON CONFLICT (id) DO NOTHING;";
  }

    @RequiredArgsConstructor
    @VisibleForTesting
    public static class PgUpdater {
      final JdbcTemplate jdbc;
      public void insert(String cmd) {
        log.debug("Adding new events to blacklist with cmd: {}", cmd);
        jdbc.execute(cmd);
      }
  }
}
