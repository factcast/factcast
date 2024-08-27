/*
 * Copyright © 2017-2024 factcast.org
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
package org.factcast.itests.factus.config;

import javax.sql.DataSource;
import lombok.NonNull;
import org.factcast.core.snap.jdbc.JdbcSnapshotCache;
import org.factcast.core.snap.jdbc.JdbcSnapshotProperties;
import org.factcast.factus.snapshot.SnapshotCache;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;

public class JdbcSnapshotCacheConfiguration {

  @Bean
  @ConfigurationProperties(prefix = JdbcSnapshotProperties.PROPERTIES_PREFIX)
  public JdbcSnapshotProperties jdbcSnapshotProperties() {
    return new JdbcSnapshotProperties();
  }

  @Bean
  public SnapshotCache snapshotCache(@NonNull JdbcSnapshotProperties props, DataSource dataSource) {
    try (var connection = dataSource.getConnection();
        var statement = connection.createStatement()) {
      statement.execute(
          """
                      CREATE TABLE IF NOT EXISTS snapshots(key VARCHAR(512), uuid VARCHAR(36), last_fact_id VARCHAR(36),\s
                          bytes BYTEA, compressed boolean, last_accessed TIMESTAMP, PRIMARY KEY (key, uuid));
                      CREATE INDEX factcast_snapshots_idx_last_accessed ON snapshots(last_accessed);""");
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return new JdbcSnapshotCache(props, dataSource);
  }
}
