/*
 * Copyright © 2017-2023 factcast.org
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
package org.factcast.store.internal.filter.blacklist;

import com.google.common.annotations.VisibleForTesting;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.factcast.store.internal.filter.PgBlacklist;
import org.springframework.jdbc.core.JdbcTemplate;

@Slf4j
public class PgBlacklistUpdater {
  private BlacklistFetcher fetcher;

  private final PgBlacklist blacklist;
  private PgUpdater pgUpdater;

  public PgBlacklistUpdater(PgBlacklist blacklist) {
    this.blacklist = blacklist;
  }

  public void updatePgBlacklist() {
    Map<UUID, String> newBlacklist = fetcher.fetchBlacklist();

    // compare and for new entries
    List<String> newBlacklistEntries = new ArrayList<String>();
    newBlacklist.forEach(
        (key, reason) -> {
          if (!blacklist.isBlocked(key)) {
            newBlacklistEntries.add("(" + key + ", " + reason + ")");
          }
        });

    if (!newBlacklistEntries.isEmpty()) {
      pgUpdater.insert(buildQuery(newBlacklistEntries));
    }
  }

  private String buildQuery(List<String> items) {
    return "INSERT INTO blacklist(id, reason) VALUES "
        + String.join(", ", items)
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
