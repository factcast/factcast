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

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.factcast.core.subscription.FactStreamHorizon;
import org.factcast.store.internal.PgConstants;
import org.springframework.jdbc.core.JdbcTemplate;

@RequiredArgsConstructor
public class ReadOnlyPgFactStreamHorizonProvider implements FactStreamHorizonProvider {

  // note that there is only one horizon with id=1, as it is meant to be a singleton
  static final String READ_HORIZON =
      "SELECT "
          + PgConstants.HORIZON_COLUMN_FACT_SER
          + ","
          + PgConstants.HORIZON_COLUMN_FACT_ID
          + ","
          + PgConstants.HORIZON_COLUMN_NOTIFICATION_SER
          + " FROM "
          + PgConstants.TABLE_HORIZON
          + " WHERE id = 1";
  private final Object instance_mutex = new Object();

  @NonNull private final DataSource primaryDataSource;
  private final AtomicReference<FactStreamHorizon> currentPrimary =
      new AtomicReference<>(FactStreamHorizon.empty());

  @Override
  public @NonNull FactStreamHorizon advance() {
    synchronized (instance_mutex) {
      FactStreamHorizon horizon = readPrimary();
      currentPrimary.set(horizon);
      return horizon;
    }
  }

  @NonNull
  protected final FactStreamHorizon readPrimary() {
    return readFrom(primaryDataSource);
  }

  @Override
  public @NonNull FactStreamHorizon currentPrimary() {
    return currentPrimary.get();
  }

  @Override
  public @NonNull FactStreamHorizon readFrom(@NonNull DataSource dataSource) {
    return jdbcTemplate(dataSource)
        .queryForObject(
            READ_HORIZON,
            (rs, rowNum) ->
                new FactStreamHorizon(
                    rs.getObject(PgConstants.HORIZON_COLUMN_FACT_ID, UUID.class),
                    rs.getLong(PgConstants.HORIZON_COLUMN_FACT_SER),
                    rs.getLong(PgConstants.HORIZON_COLUMN_NOTIFICATION_SER)));
  }

  protected JdbcTemplate jdbcTemplate(@NonNull DataSource dataSource) {
    return new JdbcTemplate(dataSource);
  }

  protected final void updateCurrentPrimary(@NonNull FactStreamHorizon horizon) {
    currentPrimary.set(horizon);
  }
}
