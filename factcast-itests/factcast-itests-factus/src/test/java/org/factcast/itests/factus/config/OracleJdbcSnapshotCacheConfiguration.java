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
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.snap.jdbc.JdbcSnapshotCache;
import org.factcast.core.snap.jdbc.JdbcSnapshotProperties;
import org.factcast.factus.snapshot.SnapshotCache;
import org.springframework.boot.context.properties.*;
import org.springframework.context.annotation.Bean;

@Slf4j
@ConfigurationProperties
@EnableConfigurationProperties
public class OracleJdbcSnapshotCacheConfiguration {

  @Bean
  @ConfigurationProperties(prefix = JdbcSnapshotProperties.PROPERTIES_PREFIX)
  public JdbcSnapshotProperties jdbcSnapshotProperties() {
    return new JdbcSnapshotProperties();
  }

  @Bean
  public SnapshotCache snapshotCache(DataSource dataSource) {
    try (var connection = dataSource.getConnection();
        var statement = connection.createStatement()) {

      statement.executeUpdate(
          """
                    CREATE TABLE factcast_snapshot (
                        projection_class VARCHAR2(512) NOT NULL,
                        aggregate_id VARCHAR2(36) NULL,
                        last_fact_id VARCHAR2(36) NOT NULL,
                        bytes BLOB,
                        snapshot_serializer_id VARCHAR2(128) NOT NULL,
                        last_accessed VARCHAR2(255),
                        CONSTRAINT uq_factcast_snapshot
                          UNIQUE (projection_class, aggregate_id)
                    )
                """);

      statement.execute(
          """
                        CREATE INDEX factcast_snapshot_last_accessed_index ON factcast_snapshot (last_accessed)
                  """);

    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    JdbcSnapshotProperties props =
        new JdbcSnapshotProperties().setSnapshotTableName("factcast_snapshot");
    return new JdbcSnapshotCache(props, dataSource);
  }
}
