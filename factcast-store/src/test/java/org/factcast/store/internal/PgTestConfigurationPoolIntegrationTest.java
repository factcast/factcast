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
package org.factcast.store.internal;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.factcast.test.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(classes = PgTestConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@IntegrationTest
class PgTestConfigurationPoolIntegrationTest {

  @Autowired DataSource dataSource;

  @Test
  void keepsIdleConnectionsLowAcrossCachedTestContexts() {
    assertThat(dataSource).isInstanceOf(org.apache.tomcat.jdbc.pool.DataSource.class);
    org.apache.tomcat.jdbc.pool.DataSource pool =
        (org.apache.tomcat.jdbc.pool.DataSource) dataSource;
    assertThat(pool.getInitialSize()).isEqualTo(1);
    assertThat(pool.getMinIdle()).isEqualTo(1);
    assertThat(pool.getMaxIdle()).isEqualTo(1);
    assertThat(pool.getMaxActive()).isEqualTo(20);
  }
}
