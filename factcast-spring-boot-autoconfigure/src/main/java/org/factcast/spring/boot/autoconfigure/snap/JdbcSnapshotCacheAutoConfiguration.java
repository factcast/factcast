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
package org.factcast.spring.boot.autoconfigure.snap;

import javax.sql.DataSource;
import lombok.NonNull;
import org.factcast.factus.Factus;
import org.factcast.factus.snapshot.SnapshotCache;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass({JdbcSnapshotCache.class, Factus.class})
@ConditionalOnMissingBean(SnapshotCache.class)
@AutoConfigureBefore({
  NoSnapshotCacheAutoConfiguration.class,
  RedissonSnapshotCacheAutoConfiguration.class
})
public class JdbcSnapshotCacheAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  @ConfigurationProperties(prefix = JdbcSnapshotProperties.PROPERTIES_PREFIX)
  public JdbcSnapshotProperties jdbcSnapshotProperties() {
    return new JdbcSnapshotProperties();
  }

  @Bean
  @ConditionalOnMissingBean
  public SnapshotCache snapshotCache(@NonNull JdbcSnapshotProperties props, DataSource dataSource) {
    return new JdbcSnapshotCache(props, dataSource);
  }
}
