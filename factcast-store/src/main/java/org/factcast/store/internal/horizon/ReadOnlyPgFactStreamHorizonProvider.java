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

import java.util.*;
import javax.sql.DataSource;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.factcast.core.subscription.FactStreamHorizon;
import org.springframework.jdbc.core.JdbcTemplate;

@RequiredArgsConstructor
public class ReadOnlyPgFactStreamHorizonProvider implements FactStreamHorizonProvider {

  static final String MAX_SERIAL = "SELECT COALESCE(MAX(ser),0) FROM fact";

  @Override
  @SuppressWarnings("java:S3551")
  public @NonNull FactStreamHorizon advance() {
    return liveHorizon();
  }

  private @NonNull FactStreamHorizon liveHorizon() {
    Long l =
        Objects.requireNonNull(
            jdbcTemplate(primaryDataSource).queryForObject(MAX_SERIAL, Long.class));
    return new FactStreamHorizon(l.longValue());
  }

  @NonNull private final DataSource primaryDataSource;

  @NonNull
  protected final FactStreamHorizon readPrimary() {
    return readFrom(primaryDataSource);
  }

  @Override
  public @NonNull FactStreamHorizon currentPrimary() {
    return readPrimary();
  }

  @Override
  public @NonNull FactStreamHorizon readFrom(@NonNull DataSource dataSource) {
    return jdbcTemplate(dataSource)
        .queryForObject(MAX_SERIAL, (rs, rowNum) -> new FactStreamHorizon(rs.getLong(1)));
  }

  protected JdbcTemplate jdbcTemplate(@NonNull DataSource dataSource) {
    return new JdbcTemplate(dataSource);
  }
}
