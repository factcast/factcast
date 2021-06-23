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
package org.factcast.store.pgsql.internal.query;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.factcast.store.pgsql.internal.PgConstants;
import org.factcast.store.pgsql.internal.PgMetrics;
import org.factcast.store.pgsql.internal.StoreMetrics.OP;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Fetches a SER from a Fact-Id.
 *
 * @author uwe.schaefer@prisma-capacity.eu
 */
@RequiredArgsConstructor
public class PgFactIdToSerialMapper {

  final JdbcTemplate jdbcTemplate;
  final PgMetrics metrics;

  /**
   * Fetches the SER of a particular Fact identified by id
   *
   * @param id the FactId to look for
   * @return the corresponding SER, 0, if no Fact is found for the id given.
   */
  // TODO add caching
  // TODO add cache hit/miss metric
  public long retrieve(UUID id) {
    if (id == null) {
      return 0;
    } else {
      return metrics.time(
          OP.SERIAL_OF,
          () -> {
            try {

              Long res =
                  jdbcTemplate.queryForObject(
                      PgConstants.SELECT_SER_BY_ID, new Object[] {id}, Long.class);
              if (res != null && res > 0) {
                return res;
              }
            } catch (EmptyResultDataAccessException ignore) {
              // ignore
            }
            return 0L;
          });
    }
  }
}
