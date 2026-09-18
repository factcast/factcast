/*
 * Copyright © 2017-2026 factcast.org
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
package org.factcast.store.internal.horizon;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.factcast.core.subscription.observer.HighWaterMark;
import org.springframework.jdbc.core.JdbcTemplate;

@RequiredArgsConstructor
public class ReadOnlyPgFactStreamHorizonProvider implements FactStreamHorizonProvider {

  static final String MISSING_HORIZON = "The singleton fact-stream horizon row is missing";
  static final String READ_HORIZON =
      "SELECT fact_ser, fact_id, notification_ser FROM factstream_horizon WHERE id = 1";

  @NonNull private final DataSource primaryDataSource;
  private final AtomicReference<FactStreamHorizon> current =
      new AtomicReference<>(FactStreamHorizon.empty());

  @Override
  public synchronized @NonNull FactStreamHorizon advance() {
    FactStreamHorizon horizon = read(primaryDataSource);
    current.set(horizon);
    return horizon;
  }

  @Override
  public @NonNull FactStreamHorizon current() {
    return current.get();
  }

  @Override
  public @NonNull FactStreamHorizon read(@NonNull DataSource dataSource) {
    List<FactStreamHorizon> horizons =
        jdbcTemplate(dataSource)
            .query(
                READ_HORIZON,
                (rs, rowNum) ->
                    new FactStreamHorizon(
                        HighWaterMark.of(
                            rs.getObject("fact_id", UUID.class), rs.getLong("fact_ser")),
                        rs.getLong("notification_ser")));
    if (horizons.isEmpty()) {
      throw new IllegalStateException(MISSING_HORIZON);
    }
    return horizons.get(0);
  }

  protected JdbcTemplate jdbcTemplate(@NonNull DataSource dataSource) {
    return new JdbcTemplate(dataSource);
  }

  protected final void updateCurrent(@NonNull FactStreamHorizon horizon) {
    current.set(horizon);
  }
}
