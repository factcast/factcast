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
package org.factcast.store.internal;

import java.util.Optional;
import java.util.UUID;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.factcast.core.store.State;
import org.factcast.core.store.StateToken;
import org.factcast.core.store.TokenStore;
import org.factcast.core.util.FactCastJson;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;

@RequiredArgsConstructor
@Slf4j
public class PgTokenStore implements TokenStore {

  final JdbcTemplate jdbc;
  final PgMetrics metrics;

  @Override
  public @NonNull StateToken create(@NonNull State state) {

    String stateAsJson = FactCastJson.writeValueAsString(state);
    UUID queryForObject = jdbc.queryForObject(PgConstants.INSERT_TOKEN, UUID.class, stateAsJson);
    return new StateToken(queryForObject);
  }

  @Override
  public void invalidate(@NonNull StateToken token) {
    metrics.time(
        StoreMetrics.OP.INVALIDATE_STATE_TOKEN,
        () -> {
          jdbc.update(PgConstants.DELETE_TOKEN, token.uuid());
        });
  }

  @Override
  public @NonNull Optional<State> get(@NonNull StateToken token) {
    try {
      String state =
          jdbc.queryForObject(PgConstants.SELECT_STATE_FROM_TOKEN, String.class, token.uuid());
      return Optional.of(FactCastJson.readValue(State.class, state));
    } catch (EmptyResultDataAccessException e) {
      return Optional.empty();
    }
  }

  @Override
  // not configurable due to cost expectation of this query
  @Scheduled(cron = "0 0 4 * * *")
  @SchedulerLock(name = "tokenStoreCompact", lockAtMostFor = "PT10m")
  public void compact() {
    log.debug("triggering compact on tokenstore");
    jdbc.update(PgConstants.COMPACT_TOKEN);
  }
}
