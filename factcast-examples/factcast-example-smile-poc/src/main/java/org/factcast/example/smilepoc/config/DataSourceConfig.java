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
package org.factcast.example.smilepoc.config;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.factcast.example.smilepoc.PocProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class DataSourceConfig {

  @Bean
  DataSource dataSource(PocProperties props) {
    String url = props.postgres().url();
    String separator = url.contains("?") ? "&" : "?";
    String tunedUrl =
        url + separator + "binaryTransfer=true&prepareThreshold=1&reWriteBatchedInserts=true";

    HikariDataSource ds = new HikariDataSource();
    ds.setJdbcUrl(tunedUrl);
    ds.setUsername(props.postgres().user());
    ds.setPassword(props.postgres().password());
    ds.setMaximumPoolSize(4);
    ds.setAutoCommit(true);
    return ds;
  }

  @Bean
  JdbcTemplate jdbcTemplate(DataSource ds) {
    return new JdbcTemplate(ds);
  }
}
