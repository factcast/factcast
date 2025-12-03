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
package org.factcast.store.internal.tail;

import com.google.common.annotations.VisibleForTesting;
import java.sql.*;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.subscription.observer.HighWaterMark;
import org.factcast.core.subscription.observer.HighWaterMarkFetcher;
import org.factcast.store.internal.PgConstants;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

@Slf4j
@RequiredArgsConstructor
public class SimpleHighWaterMarkFetcher implements HighWaterMarkFetcher {
  // the complexity toll of memorizing/refreshing/expiring here far exceeds its usefulness,
  // as we use it once per subscription and the query very likely is an o(1)

  @Override
  @NonNull
  public HighWaterMark highWaterMark(@NonNull DataSource ds) {
    try {
      // the rowMapper can't return null, but better safe than sorry (also makes sonar happy)
      return Optional.ofNullable(
              jdbcTemplate(ds).queryForObject(PgConstants.HIGHWATER_MARK, this::extract))
          .orElse(HighWaterMark.empty());
    } catch (EmptyResultDataAccessException noFactsAtAll) {
      // ignore but resetting target to initial values, can happen in integration tests when
      // facts are wiped between runs
      return HighWaterMark.empty();
    }
  }

  @VisibleForTesting
  @NonNull
  protected JdbcTemplate jdbcTemplate(@NonNull DataSource ds) {
    return new JdbcTemplate(ds);
  }

  @VisibleForTesting
  @NonNull
  protected HighWaterMark extract(@NonNull ResultSet rs, int i) throws SQLException {
    return HighWaterMark.of(rs.getObject("targetId", UUID.class), rs.getLong("targetSer"));
  }
}
