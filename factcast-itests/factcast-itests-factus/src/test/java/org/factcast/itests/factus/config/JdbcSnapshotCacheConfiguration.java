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
import org.factcast.core.snap.jdbc.JdbcSnapshotCache;
import org.factcast.core.snap.jdbc.JdbcSnapshotProperties;
import org.factcast.factus.snapshot.SnapshotCache;
import org.springframework.boot.context.properties.*;
import org.springframework.context.annotation.Bean;

@ConfigurationProperties
@EnableConfigurationProperties
public class JdbcSnapshotCacheConfiguration {

  @Bean
  @ConfigurationProperties(prefix = JdbcSnapshotProperties.PROPERTIES_PREFIX)
  public JdbcSnapshotProperties jdbcSnapshotProperties() {
    return new JdbcSnapshotProperties();
  }

  @Bean
  public SnapshotCache snapshotCache(DataSource dataSource) {
    try (var connection = dataSource.getConnection();
        var statement = connection.createStatement()) {
      statement.execute(
          """
                            CREATE TABLE IF NOT EXISTS my_snapshot_table(projection_class VARCHAR(512), aggregate_id VARCHAR(36) NULL, last_fact_id VARCHAR(36),
                                bytes BYTEA, snapshot_serializer_id VARCHAR(128), PRIMARY KEY (projection_class, aggregate_id));
                            """);
      statement.execute(
          """
                            CREATE TABLE IF NOT EXISTS my_snapshot_table_last_accessed(projection_class VARCHAR(512), aggregate_id VARCHAR(36) NULL,
                            last_accessed VARCHAR, PRIMARY KEY (projection_class, aggregate_id));
                            CREATE INDEX IF NOT EXISTS my_snapshot_table_index ON my_snapshot_table_last_accessed(last_accessed);
                            """);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    JdbcSnapshotProperties props =
        new JdbcSnapshotProperties()
            .setSnapshotTableName("my_snapshot_table")
            .setSnapshotAccessTableName("my_snapshot_table_last_accessed");
    return new JdbcSnapshotCache(props, dataSource);
  }
}
