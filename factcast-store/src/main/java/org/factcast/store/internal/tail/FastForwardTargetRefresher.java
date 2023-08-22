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

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.subscription.observer.FastForwardTarget;
import org.factcast.store.internal.PgConstants;
import org.jetbrains.annotations.Nullable;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;

@Slf4j
@RequiredArgsConstructor
public class FastForwardTargetRefresher implements FastForwardTarget {
  private final JdbcTemplate jdbc;

  private HighWaterMark target = HighWaterMark.create();

  @Nullable
  @Override
  public UUID targetId() {
    return target.targetId;
  }

  @Override
  public long targetSer() {
    return target.targetSer;
  }

  @Scheduled(fixedRate = 5, timeUnit = TimeUnit.MINUTES)
  public void refresh() {
    log.debug("Triggering fast forward target refresh");

    try {
      target =
          jdbc.queryForObject(
              PgConstants.HIGHWATER_MARK,
              (rs, rowNum) -> {
                final var targetId = rs.getObject("targetId", UUID.class);
                final var targetSer = rs.getLong("targetSer");

                return HighWaterMark.of(targetId, targetSer);
              });
    } catch (EmptyResultDataAccessException noFactsAtAll) {
      // ignore but resetting target to initial values, can happen in integration tests when facts
      // are wiped between runs
      target = HighWaterMark.create();
    }
  }

  @Value(staticConstructor = "of")
  static class HighWaterMark {
    UUID targetId;
    long targetSer;

    static HighWaterMark create() {
      return of(null, 0);
    }
  }
}
