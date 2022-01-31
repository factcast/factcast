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
package org.factcast.store.internal.check;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.factcast.store.internal.PgConstants;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;

@Slf4j
@RequiredArgsConstructor
public class IndexCheck {
  private final JdbcTemplate jdbc;

  @Scheduled(cron = "${factcast.store.indexCheckCron:0 0 3 * * *}")
  public void checkIndexes() {
    log.debug("checking indexes");
    List<String> invalid = jdbc.queryForList(PgConstants.BROKEN_INDEX_NAMES, String.class);

    log.debug("found {} invalid index(es)", invalid.size() > 0 ? invalid.size() : "no");
    invalid.forEach(s -> log.warn("Detected invalid index: {}", s));
  }
}
