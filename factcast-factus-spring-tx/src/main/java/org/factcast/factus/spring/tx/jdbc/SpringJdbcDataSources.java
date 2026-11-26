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
package org.factcast.factus.spring.tx.jdbc;

import javax.sql.DataSource;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;

/**
 * Splits the DataSource behind a JdbcTemplate into the two the plain JDBC building blocks need: the
 * lock must be committed as it is taken, the fact stream position must commit with the projection.
 */
@UtilityClass
public class SpringJdbcDataSources {

  /**
   * Bypasses an ongoing transaction, so that the lease is committed when it is taken and outlives a
   * rollback of the projection's transaction.
   */
  public DataSource forLock(@NonNull JdbcTemplate jdbcTemplate) {
    DataSource dataSource = dataSourceOf(jdbcTemplate);
    if (dataSource instanceof TransactionAwareDataSourceProxy proxy) {
      return proxy.getTargetDataSource();
    }
    return dataSource;
  }

  /** Joins an ongoing transaction, so that the position commits with the projection's updates. */
  public DataSource forPosition(@NonNull JdbcTemplate jdbcTemplate) {
    DataSource dataSource = dataSourceOf(jdbcTemplate);
    if (dataSource instanceof TransactionAwareDataSourceProxy) {
      return dataSource;
    }
    return new TransactionAwareDataSourceProxy(dataSource);
  }

  private DataSource dataSourceOf(JdbcTemplate jdbcTemplate) {
    DataSource dataSource = jdbcTemplate.getDataSource();
    if (dataSource == null) {
      throw new IllegalArgumentException("The given JdbcTemplate has no DataSource");
    }
    return dataSource;
  }
}
