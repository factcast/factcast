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
package org.factcast.store.internal.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import org.factcast.store.internal.PgTestConfiguration;
import org.factcast.test.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.postgresql.jdbc.PgConnection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(PgTestConfiguration.class)
@IntegrationTest
class PgBatchInsertRewriteConfigurerIntegrationTest {
  @Autowired org.apache.tomcat.jdbc.pool.DataSource existing;

  @Test
  void everyNewPhysicalConnectionHasRewritingEnabledDespiteExplicitFalse() throws Exception {
    var ds = new org.apache.tomcat.jdbc.pool.DataSource();
    try {
      ds.setUrl(existing.getUrl() + "&reWriteBatchedInserts=false");
      ds.setUsername(existing.getUsername());
      ds.setPassword(existing.getPoolProperties().getPassword());
      ds.setDriverClassName("org.postgresql.Driver");
      ds.setInitialSize(0);
      ds.setMaxActive(2);
      new PgBatchInsertRewriteConfigurer().postProcessBeforeInitialization(ds, "dataSource");
      try (var first = ds.getConnection();
          var second = ds.getConnection()) {
        assertThat(
                first
                    .unwrap(PgConnection.class)
                    .getQueryExecutor()
                    .isReWriteBatchedInsertsEnabled())
            .isTrue();
        assertThat(
                second
                    .unwrap(PgConnection.class)
                    .getQueryExecutor()
                    .isReWriteBatchedInsertsEnabled())
            .isTrue();
      }
    } finally {
      ds.close();
    }
  }
}
