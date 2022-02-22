/*
 * Copyright © 2017-2020 factcast.org
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
package org.factcast.store.internal.query;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.factcast.store.internal.PgConstants;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.rowset.SqlRowSet;

/**
 * Fetches the latest SERIAL from the fact table.
 *
 * @author uwe.schaefer@prisma-capacity.eu
 */
@RequiredArgsConstructor
@Slf4j
public class PgLatestSerialFetcher {

  @NonNull final JdbcTemplate jdbcTemplate;

  /** @return 0, if no Fact is found, or exception is raised. */
  public long retrieveLatestSer() {
    // noinspection CatchMayIgnoreException
    try {
      SqlRowSet rs = jdbcTemplate.queryForRowSet(PgConstants.LAST_SERIAL_IN_LOG);
      if (rs.next()) {
        return rs.getLong(1);
      }
    } catch (Exception ignored) {
      log.warn("While retrieveLatestSer:", ignored);
    }
    return 0;
  }
}
