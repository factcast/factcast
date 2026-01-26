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
package org.factcast.itests.factus.client;

import org.factcast.factus.snapshot.SnapshotCache;
import org.factcast.itests.TestFactusApplication;
import org.factcast.itests.factus.config.JdbcSnapshotCacheConfigurationForMySql;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.mysql.MySQLContainer;

@SpringBootTest
@ContextConfiguration(
    classes = {TestFactusApplication.class, JdbcSnapshotCacheConfigurationForMySql.class})
@DirtiesContext
public class JdbcSnapshotCacheWithMySqlITest extends SnapshotCacheTest {

  @Container
  static MySQLContainer mysql =
      new MySQLContainer("mysql:8.4")
          .withDatabaseName("testdb")
          .withUsername("testuser")
          .withPassword("testpass");

  @DynamicPropertySource
  static void mysqlProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", mysql::getJdbcUrl);
    registry.add("spring.datasource.username", mysql::getUsername);
    registry.add("spring.datasource.password", mysql::getPassword);
  }

  @Autowired
  public JdbcSnapshotCacheWithMySqlITest(SnapshotCache repository) {
    super(repository);
  }
}
