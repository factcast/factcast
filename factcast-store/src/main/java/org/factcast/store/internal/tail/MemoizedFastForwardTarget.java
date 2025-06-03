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
import java.time.Duration;
import java.util.UUID;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.subscription.observer.FastForwardTarget;
import org.factcast.core.subscription.observer.HighWaterMark;
import org.factcast.store.internal.PgConstants;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

@Slf4j
@RequiredArgsConstructor
public class MemoizedFastForwardTarget implements FastForwardTarget {
  private static final long REFRESH_AFTER_MS = Duration.ofMinutes(5).toMillis();
  private final JdbcTemplate jdbc;
  private HighWaterMark highWaterMark;
  private long lastFetched = 0;

  @Override
  public HighWaterMark highWaterMark() {
    long now = System.currentTimeMillis();
    if (highWaterMark == null || highWaterMark.isEmpty() || needsRefresh(now)) {
      try {
        log.debug("fetching fast forward target");

        highWaterMark =
            jdbc.queryForObject(
                PgConstants.HIGHWATER_MARK,
                (rs, rowNum) -> {
                  final var targetId = rs.getObject("targetId", UUID.class);
                  final var targetSer = rs.getLong("targetSer");

                  return HighWaterMark.of(targetId, targetSer);
                });
        lastFetched = now;
      } catch (EmptyResultDataAccessException noFactsAtAll) {
        // ignore but resetting target to initial values, can happen in integration tests when
        // facts
        // are wiped between runs
        highWaterMark = HighWaterMark.empty();
      }
    }
    return highWaterMark;
  }

  @VisibleForTesting
  boolean needsRefresh(long now) {
    return highWaterMark == null || now - lastFetched > REFRESH_AFTER_MS;
  }

  @VisibleForTesting
  public void expire() {
    highWaterMark = null;
  }
}
